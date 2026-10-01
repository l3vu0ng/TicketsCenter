SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC(N'
CREATE OR ALTER VIEW dbo.vw_EventCancellationExceptions
AS
    SELECT o.event_id, CAST(''USED_TICKET'' AS varchar(40)) exception_type,
           t.id exception_id, o.id order_id, t.status
    FROM dbo.tc_tickets t
    JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
    JOIN dbo.tc_orders o ON o.id = oi.order_id
    JOIN dbo.tc_events e ON e.id = o.event_id
    WHERE e.status = ''CANCELLED'' AND t.status = ''USED''
    UNION ALL
    SELECT o.event_id, CAST(''REFUND_RESULT'' AS varchar(40)),
           r.id, o.id, r.status
    FROM dbo.tc_refunds r
    JOIN dbo.tc_refund_requests rr ON rr.id = r.refund_request_id
    JOIN dbo.tc_orders o ON o.id = rr.order_id
    JOIN dbo.tc_events e ON e.id = o.event_id
    WHERE e.status = ''CANCELLED'' AND rr.reason_type = ''EVENT_CANCELLATION''
      AND r.status IN (''FAILED'', ''UNKNOWN'');
');

EXEC(N'
CREATE OR ALTER VIEW dbo.vw_EventCancellationWork
AS
    SELECT h.event_id, CAST(''HOLD'' AS varchar(10)) work_type, h.id work_id
    FROM dbo.tc_ticket_holds h
    JOIN dbo.tc_events e ON e.id = h.event_id
    WHERE e.status = ''CANCELLED'' AND h.status = ''ACTIVE''
      AND NOT EXISTS (SELECT 1 FROM dbo.tc_orders o WHERE o.hold_id = h.id)
      AND EXISTS (
          SELECT 1 FROM dbo.tc_outbox x
          WHERE x.event_type = ''EVENT_CANCELLED'' AND x.aggregate_id = h.event_id
            AND x.status <> ''PUBLISHED'')
    UNION ALL
    SELECT o.event_id, CAST(''ORDER'' AS varchar(10)), o.id
    FROM dbo.tc_orders o
    JOIN dbo.tc_events e ON e.id = o.event_id
    WHERE e.status = ''CANCELLED''
      AND EXISTS (
          SELECT 1 FROM dbo.tc_outbox x
          WHERE x.event_type = ''EVENT_CANCELLED'' AND x.aggregate_id = o.event_id
            AND x.status <> ''PUBLISHED'')
      AND (
          EXISTS (SELECT 1 FROM dbo.tc_ticket_holds h WHERE h.id = o.hold_id AND h.status = ''ACTIVE'')
          OR EXISTS (
              SELECT 1 FROM dbo.tc_tickets t
              JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
              WHERE oi.order_id = o.id AND t.status = ''ACTIVE'')
          OR EXISTS (
              SELECT 1 FROM dbo.tc_refund_requests rr
              WHERE rr.order_id = o.id AND rr.status = ''PENDING''));
');

EXEC(N'
CREATE OR ALTER VIEW dbo.vw_EventCancellationProgress
AS
    SELECT e.id event_id, e.status event_status,
           (SELECT COUNT_BIG(*) FROM dbo.tc_orders o WHERE o.event_id = e.id) total_orders,
           (SELECT COUNT_BIG(*) FROM dbo.tc_orders o
            WHERE o.event_id = e.id AND (
                EXISTS (SELECT 1 FROM dbo.tc_ticket_holds h WHERE h.id = o.hold_id AND h.status = ''ACTIVE'')
                OR EXISTS (SELECT 1 FROM dbo.tc_order_items oi JOIN dbo.tc_tickets t ON t.order_item_id = oi.id
                           WHERE oi.order_id = o.id AND t.status = ''ACTIVE'')
                OR EXISTS (SELECT 1 FROM dbo.tc_refund_requests rr WHERE rr.order_id = o.id AND rr.status = ''PENDING'')
                OR EXISTS (SELECT 1 FROM dbo.tc_refund_requests rr
                           LEFT JOIN dbo.tc_refunds r ON r.refund_request_id = rr.id
                           WHERE rr.order_id = o.id AND rr.reason_type = ''EVENT_CANCELLATION''
                             AND rr.status = ''APPROVED'' AND (r.id IS NULL OR r.status = ''PENDING'')))) pending_orders,
           (SELECT COUNT_BIG(*) FROM dbo.tc_orders o
            WHERE o.event_id = e.id
              AND NOT EXISTS (SELECT 1 FROM dbo.tc_ticket_holds h WHERE h.id = o.hold_id AND h.status = ''ACTIVE'')
              AND NOT EXISTS (SELECT 1 FROM dbo.tc_order_items oi JOIN dbo.tc_tickets t ON t.order_item_id = oi.id
                              WHERE oi.order_id = o.id AND t.status NOT IN (''REFUNDED'', ''INVALIDATED''))
              AND NOT EXISTS (SELECT 1 FROM dbo.vw_EventCancellationExceptions x WHERE x.order_id = o.id)) completed_orders,
           (SELECT COUNT_BIG(*) FROM dbo.vw_EventCancellationExceptions x WHERE x.event_id = e.id) exception_count,
           (SELECT COUNT_BIG(*) FROM dbo.tc_refund_requests rr JOIN dbo.tc_orders o ON o.id = rr.order_id
            WHERE o.event_id = e.id AND rr.reason_type = ''EVENT_CANCELLATION'') requests_created,
           (SELECT COUNT_BIG(*) FROM dbo.tc_refunds r JOIN dbo.tc_refund_requests rr ON rr.id = r.refund_request_id
            JOIN dbo.tc_orders o ON o.id = rr.order_id
            WHERE o.event_id = e.id AND rr.reason_type = ''EVENT_CANCELLATION'' AND r.status = ''SUCCEEDED'') refunds_succeeded,
           (SELECT COUNT_BIG(*) FROM dbo.tc_refunds r JOIN dbo.tc_refund_requests rr ON rr.id = r.refund_request_id
            JOIN dbo.tc_orders o ON o.id = rr.order_id
            WHERE o.event_id = e.id AND rr.reason_type = ''EVENT_CANCELLATION'' AND r.status = ''PENDING'') refunds_pending
    FROM dbo.tc_events e;
');

EXEC(N'
CREATE OR ALTER PROCEDURE dbo.usp_CancelEvent @event_id uniqueidentifier, @actor_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp13;
    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = ''ADMIN'')
            THROW 51530, ''Administrator role required'', 1;
        DECLARE @status varchar(30), @start datetime2(3);
        SELECT @status = status, @start = start_time
        FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        IF @status IS NULL THROW 51531, ''Event not found'', 1;
        IF @status = ''CANCELLED'' BEGIN
            SELECT @event_id event_id, @status status;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @status <> ''PUBLISHED'' OR SYSUTCDATETIME() >= @start
            THROW 51532, ''Only a future published event can be cancelled'', 1;
        UPDATE dbo.tc_events SET status = ''CANCELLED'', version = version + 1 WHERE id = @event_id;
        INSERT dbo.tc_outbox(event_type, aggregate_type, aggregate_id, payload, idempotency_key)
        VALUES (''EVENT_CANCELLED'', ''EVENT'', @event_id,
                CONCAT(N''{"eventId":"'', @event_id, N''"}''), CONCAT(''event-cancelled:'', @event_id));
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @event_id event_id, CAST(''CANCELLED'' AS varchar(30)) status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp13;
        THROW;
    END CATCH
END;
');

EXEC(N'
CREATE OR ALTER PROCEDURE dbo.usp_ProcessCancelledOrder @event_id uniqueidentifier, @order_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp17;
    BEGIN TRY
        IF IS_ROLEMEMBER(''tc_worker'') <> 1 AND IS_ROLEMEMBER(''db_owner'') <> 1
            THROW 51569, ''Worker principal required'', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK)
                       WHERE id = @event_id AND status = ''CANCELLED'')
            THROW 51570, ''Event is not cancelled'', 1;
        DECLARE @hold_id uniqueidentifier, @order_status varchar(30), @owner_id uniqueidentifier,
                @request_id uniqueidentifier;
        SELECT @hold_id = hold_id, @order_status = status, @owner_id = user_id
        FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK)
        WHERE id = @order_id AND event_id = @event_id;
        IF @hold_id IS NULL THROW 51571, ''Order does not belong to cancelled event'', 1;
        IF @order_status IN (''PENDING_PAYMENT'', ''EXPIRED'', ''CANCELLED'') BEGIN
            IF EXISTS (SELECT 1 FROM dbo.tc_ticket_holds WHERE id = @hold_id AND status = ''ACTIVE'')
                EXEC dbo.usp_ReleaseTicketHold @hold_id, NULL, ''EVENT_CANCELLED'';
            IF @started = 1 COMMIT TRANSACTION;
            SELECT @order_id order_id, CAST(''RELEASED'' AS varchar(30)) result; RETURN;
        END;

        SELECT t.id FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
        WHERE oi.order_id = @order_id ORDER BY t.id;

        INSERT dbo.tc_audit_logs(action, aggregate_type, aggregate_id, detail)
        SELECT ''CANCELLED_EVENT_USED_TICKET'', ''TICKET'', t.id,
               CONCAT(N''{"orderId":"'', @order_id, N''"}'')
        FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
        WHERE oi.order_id = @order_id AND t.status = ''USED''
          AND NOT EXISTS (SELECT 1 FROM dbo.tc_audit_logs a
                          WHERE a.action = ''CANCELLED_EVENT_USED_TICKET'' AND a.aggregate_id = t.id);

        UPDATE rr SET reason_type = ''EVENT_CANCELLATION'', version = version + 1
        FROM dbo.tc_refund_requests rr
        WHERE rr.order_id = @order_id AND rr.status = ''PENDING''
          AND rr.reason_type = ''CUSTOMER_REQUEST'';

        UPDATE rr SET status = ''COMPLETED'', decided_at = COALESCE(decided_at, SYSUTCDATETIME()),
                      version = version + 1
        FROM dbo.tc_refund_requests rr
        WHERE rr.order_id = @order_id AND rr.status = ''PENDING''
          AND rr.reason_type = ''EVENT_CANCELLATION''
          AND NOT EXISTS (SELECT 1 FROM dbo.tc_refund_request_tickets rt WHERE rt.refund_request_id = rr.id);

        DECLARE @pending TABLE(id uniqueidentifier PRIMARY KEY);
        INSERT @pending
        SELECT id FROM dbo.tc_refund_requests
        WHERE order_id = @order_id AND status = ''PENDING'' AND reason_type = ''EVENT_CANCELLATION'';
        WHILE EXISTS (SELECT 1 FROM @pending) BEGIN
            SELECT TOP (1) @request_id = id FROM @pending ORDER BY id;
            EXEC dbo.usp_ReviewRefundRequest @request_id, NULL, ''APPROVE'', NULL, 0;
            DELETE FROM @pending WHERE id = @request_id;
        END;

        IF EXISTS (
            SELECT 1 FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
            WHERE oi.order_id = @order_id AND t.status = ''ACTIVE'')
        BEGIN
            SET @request_id = NEWID();
            INSERT dbo.tc_refund_requests(id, order_id, requester_id, reason, reason_type)
            VALUES (@request_id, @order_id, @owner_id, N''Event cancelled'', ''EVENT_CANCELLATION'');
            INSERT dbo.tc_refund_request_tickets(refund_request_id, ticket_id)
            SELECT @request_id, t.id FROM dbo.tc_tickets t
            JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
            WHERE oi.order_id = @order_id AND t.status = ''ACTIVE'';
            UPDATE t SET status = ''REFUND_PENDING'', version = version + 1
            FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
            WHERE oi.order_id = @order_id AND t.status = ''ACTIVE'';
            EXEC dbo.usp_ReviewRefundRequest @request_id, NULL, ''APPROVE'', NULL, 0;
        END;

        IF @started = 1 COMMIT TRANSACTION;
        SELECT @order_id order_id, @request_id refund_request_id,
               CAST(''PROCESSED'' AS varchar(30)) result;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp17;
        THROW;
    END CATCH
END;
');

GRANT SELECT ON dbo.vw_EventCancellationProgress TO tc_platform_admin;
GRANT SELECT ON dbo.vw_EventCancellationExceptions TO tc_platform_admin;
GRANT SELECT ON dbo.vw_EventCancellationWork TO tc_worker;
GRANT SELECT ON dbo.tc_outbox TO tc_worker;
GRANT UPDATE (status, published_at, lease_owner, lease_until, attempts, available_at)
ON OBJECT::dbo.tc_outbox TO tc_worker;
