SET NOCOUNT ON;
SET XACT_ABORT ON;

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.tc_refunds') AND name = 'IX_Refund_Status_CreatedAt')
    CREATE INDEX IX_Refund_Status_CreatedAt ON dbo.tc_refunds(status, created_at, id)
    INCLUDE (amount, provider_reference, payment_id, purpose, refund_request_id);

EXEC(N'
CREATE OR ALTER VIEW dbo.vw_RefundWork
AS
    SELECT id, amount, status, provider_reference, created_at
    FROM dbo.tc_refunds
    WHERE status IN (''PENDING'', ''UNKNOWN'');
');

EXEC(N'
CREATE OR ALTER VIEW dbo.vw_FailedCompensationAttempts
AS
    SELECT DISTINCT r.payment_id, p.order_id
    FROM dbo.tc_refunds r
    JOIN dbo.tc_payments p ON p.id = r.payment_id
    WHERE r.purpose = ''PAYMENT_COMPENSATION'' AND r.status = ''FAILED''
      AND NOT EXISTS (
          SELECT 1 FROM dbo.tc_refunds active
          WHERE active.payment_id = r.payment_id AND active.purpose = ''PAYMENT_COMPENSATION''
            AND active.status IN (''PENDING'', ''UNKNOWN'', ''SUCCEEDED''));
');

EXEC(N'
CREATE OR ALTER PROCEDURE dbo.usp_ApplyRefundResult @refund_id uniqueidentifier, @verified_result nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp11;
    BEGIN TRY
        IF COALESCE(IS_ROLEMEMBER(''tc_worker''), 0) <> 1 AND COALESCE(IS_ROLEMEMBER(''db_owner''), 0) <> 1
            THROW 51409, ''Worker principal required'', 1;
        IF ISJSON(@verified_result) <> 1 THROW 51410, ''Verified result must be JSON'', 1;
        DECLARE @result varchar(20) = JSON_VALUE(@verified_result, ''$.status''),
                @provider_reference nvarchar(200) = JSON_VALUE(@verified_result, ''$.providerReference''),
                @request_id uniqueidentifier, @payment_id uniqueidentifier, @purpose varchar(30),
                @amount decimal(19,0), @current varchar(20), @order_id uniqueidentifier,
                @event_id uniqueidentifier, @payment_amount decimal(19,0),
                @now datetime2(3) = SYSUTCDATETIME();
        IF @result NOT IN (''SUCCEEDED'', ''FAILED'', ''UNKNOWN'') THROW 51411, ''Unsupported refund result'', 1;
        IF @provider_reference IS NULL OR LEN(@provider_reference) = 0 THROW 51415, ''Provider reference is required'', 1;

        SELECT @payment_id = payment_id FROM dbo.tc_refunds WHERE id = @refund_id;
        IF @payment_id IS NULL THROW 51412, ''Refund not found'', 1;
        SELECT @order_id = order_id FROM dbo.tc_payments WHERE id = @payment_id;
        SELECT @event_id = event_id FROM dbo.tc_orders WHERE id = @order_id;
        SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT 1 FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE id = @order_id;
        SELECT @payment_amount = amount FROM dbo.tc_payments WITH (UPDLOCK, HOLDLOCK) WHERE id = @payment_id;
        SELECT id FROM dbo.tc_refunds WITH (UPDLOCK, HOLDLOCK) WHERE payment_id = @payment_id ORDER BY id;
        SELECT @request_id = refund_request_id, @purpose = purpose, @amount = amount, @current = status
        FROM dbo.tc_refunds WHERE id = @refund_id AND payment_id = @payment_id;
        IF @current IS NULL THROW 51412, ''Refund not found'', 1;
        IF @request_id IS NOT NULL
            SELECT 1 FROM dbo.tc_refund_requests WITH (UPDLOCK, HOLDLOCK) WHERE id = @request_id;

        IF @current = ''SUCCEEDED'' BEGIN
            SELECT @refund_id refund_id, @current refund_status;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @current = ''FAILED'' AND @result <> ''FAILED''
            THROW 51414, ''Failed refund is terminal; create an explicitly authorized retry'', 1;
        IF @result = ''SUCCEEDED'' AND
           (SELECT COALESCE(SUM(amount), 0) FROM dbo.tc_refunds WHERE payment_id = @payment_id AND status = ''SUCCEEDED'') + @amount > @payment_amount
            THROW 51413, ''Successful refunds exceed captured payment'', 1;

        IF @result = ''SUCCEEDED'' AND @purpose = ''CUSTOMER_REFUND'' BEGIN
            SELECT t.id FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK)
            JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_request_id = @request_id ORDER BY t.id;
            SELECT z.id FROM dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK) WHERE EXISTS (
                SELECT 1 FROM dbo.tc_refund_request_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
                JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                WHERE rt.refund_request_id = @request_id AND oi.zone_id = z.id) ORDER BY z.id;
            SELECT s.id FROM dbo.tc_seats s WITH (UPDLOCK, HOLDLOCK) WHERE EXISTS (
                SELECT 1 FROM dbo.tc_refund_request_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
                JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                WHERE rt.refund_request_id = @request_id AND oi.seat_id = s.id) ORDER BY s.id;
            IF EXISTS (
                SELECT 1 FROM dbo.tc_zones z JOIN (
                    SELECT oi.zone_id, COUNT_BIG(*) quantity FROM dbo.tc_refund_request_tickets rt
                    JOIN dbo.tc_tickets t ON t.id = rt.ticket_id JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                    WHERE rt.refund_request_id = @request_id AND oi.seat_id IS NULL AND t.status = ''REFUND_PENDING''
                    GROUP BY oi.zone_id
                ) q ON q.zone_id = z.id WHERE z.sold_quantity < q.quantity)
                THROW 51416, ''Sold standing inventory is inconsistent'', 1;
        END;

        UPDATE dbo.tc_refunds SET status = @result, provider_reference = @provider_reference,
               processed_at = CASE WHEN @result IN (''SUCCEEDED'', ''FAILED'') THEN @now ELSE processed_at END,
               version = version + 1 WHERE id = @refund_id;

        IF @result = ''SUCCEEDED'' AND @purpose = ''CUSTOMER_REFUND'' BEGIN
            UPDATE s SET status = ''AVAILABLE'', version = s.version + 1
            FROM dbo.tc_seats s JOIN dbo.tc_order_items oi ON oi.seat_id = s.id
            JOIN dbo.tc_tickets t ON t.order_item_id = oi.id JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_request_id = @request_id AND t.status = ''REFUND_PENDING'' AND s.status = ''SOLD'';
            UPDATE z SET sold_quantity = sold_quantity - q.quantity, version = z.version + 1
            FROM dbo.tc_zones z JOIN (
                SELECT oi.zone_id, COUNT_BIG(*) quantity FROM dbo.tc_refund_request_tickets rt
                JOIN dbo.tc_tickets t ON t.id = rt.ticket_id JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
                WHERE rt.refund_request_id = @request_id AND oi.seat_id IS NULL AND t.status = ''REFUND_PENDING''
                GROUP BY oi.zone_id
            ) q ON q.zone_id = z.id;
            UPDATE t SET status = ''REFUNDED'', version = t.version + 1
            FROM dbo.tc_tickets t JOIN dbo.tc_refund_request_tickets rt ON rt.ticket_id = t.id
            WHERE rt.refund_request_id = @request_id AND t.status = ''REFUND_PENDING'';
            UPDATE dbo.tc_refund_request_tickets SET is_open = 0 WHERE refund_request_id = @request_id;
            UPDATE dbo.tc_refund_requests SET status = ''COMPLETED'', version = version + 1 WHERE id = @request_id;
        END;

        INSERT dbo.tc_audit_logs(action, aggregate_type, aggregate_id, detail)
        VALUES (CONCAT(''REFUND_'', @result), ''REFUND'', @refund_id, @provider_reference);
        IF @result = ''SUCCEEDED'' INSERT dbo.tc_outbox(event_type, aggregate_type, aggregate_id, payload, idempotency_key)
        VALUES (''REFUND_SUCCEEDED'', ''REFUND'', @refund_id,
                CONCAT(N''{"refundId":"'', @refund_id, N''"}''), CONCAT(''refund-succeeded:'', @refund_id));
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @refund_id refund_id, @result refund_status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp11;
        THROW;
    END CATCH
END;
');

GRANT SELECT ON dbo.vw_RefundWork TO tc_worker;
GRANT SELECT ON dbo.vw_FailedCompensationAttempts TO tc_worker;
GRANT EXECUTE ON dbo.usp_ApplyRefundResult TO tc_worker;
