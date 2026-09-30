SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC(N'
CREATE OR ALTER PROCEDURE dbo.usp_PublishEvent
    @event_id uniqueidentifier, @actor_id uniqueidentifier, @commission_rule_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp12;
    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = ''ADMIN'')
            THROW 51520, ''Administrator role required'', 1;
        DECLARE @organization_id uniqueidentifier, @status varchar(30), @now datetime2(3) = SYSUTCDATETIME();
        SELECT @organization_id = organization_id, @status = status FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        IF @status IS NULL THROW 51521, ''Event not found'', 1;
        IF @status = ''PUBLISHED'' BEGIN
            IF (SELECT commission_rule_id FROM dbo.tc_events WHERE id = @event_id) <> @commission_rule_id
                THROW 51522, ''Published event commission rule is immutable'', 1;
            SELECT @event_id event_id, @status status;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @status <> ''PENDING_APPROVAL'' THROW 51523, ''Event is not pending approval'', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_commission_rules WITH (UPDLOCK, HOLDLOCK)
                       WHERE id = @commission_rule_id AND organization_id = @organization_id
                         AND effective_from <= @now AND effective_to > @now)
            THROW 51524, ''Commission rule is not valid for event organization'', 1;
        IF EXISTS (SELECT 1 FROM dbo.tc_events WHERE id = @event_id AND (cover_image_url IS NULL OR sale_start >= sale_end OR sale_end > start_time OR start_time >= end_time))
            THROW 51525, ''Event content or schedule is incomplete'', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_zones WITH (UPDLOCK, HOLDLOCK) WHERE event_id = @event_id)
            THROW 51526, ''Event requires at least one zone'', 1;
        IF EXISTS (SELECT 1 FROM dbo.tc_zones z WHERE z.event_id = @event_id AND z.type = ''SEATED''
                   AND NOT EXISTS (SELECT 1 FROM dbo.tc_seats s WHERE s.zone_id = z.id))
            THROW 51527, ''Each seated zone requires seats'', 1;
        UPDATE dbo.tc_events SET commission_rule_id = @commission_rule_id, status = ''PUBLISHED'', version = version + 1 WHERE id = @event_id;
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @event_id event_id, CAST(''PUBLISHED'' AS varchar(30)) status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp12;
        THROW;
    END CATCH
END;
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
        SELECT @status = status, @start = start_time FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        IF @status IS NULL THROW 51531, ''Event not found'', 1;
        IF @status = ''CANCELLED'' BEGIN
            SELECT @event_id event_id, @status status;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @status <> ''PUBLISHED'' OR SYSUTCDATETIME() >= @start THROW 51532, ''Only a future published event can be cancelled'', 1;
        UPDATE dbo.tc_events SET status = ''CANCELLED'', version = version + 1 WHERE id = @event_id;
        INSERT dbo.tc_outbox(event_type, aggregate_type, aggregate_id, payload, idempotency_key)
        VALUES (''EVENT_CANCELLED'', ''EVENT'', @event_id, CONCAT(N''{"eventId":"'', @event_id, N''"}''), CONCAT(''event-cancelled:'', @event_id));
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
CREATE OR ALTER PROCEDURE dbo.usp_CalculateEventSettlement @event_id uniqueidentifier, @actor_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp14;
    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = ''ADMIN'')
            THROW 51540, ''Administrator role required'', 1;
        DECLARE @rule_id uniqueidentifier, @rate decimal(7,4), @fixed decimal(19,0), @settlement_id uniqueidentifier,
                @settlement_status varchar(20), @gross decimal(19,0), @refund decimal(19,0), @commission decimal(19,0), @net decimal(19,0);
        SELECT @rule_id = commission_rule_id FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        IF @rule_id IS NULL THROW 51541, ''Event has no applied commission rule'', 1;
        SELECT @rate = rate_percent, @fixed = fixed_fee FROM dbo.tc_commission_rules WHERE id = @rule_id;
        SELECT o.id FROM dbo.tc_orders o WITH (UPDLOCK, HOLDLOCK) WHERE o.event_id = @event_id ORDER BY o.id;
        SELECT @settlement_id = id, @settlement_status = status FROM dbo.tc_settlements WITH (UPDLOCK, HOLDLOCK) WHERE event_id = @event_id;
        IF @settlement_status IN (''CONFIRMED'', ''PAID'') THROW 51542, ''Confirmed settlement cannot be recalculated'', 1;
        DECLARE @items TABLE(order_id uniqueidentifier PRIMARY KEY, gross decimal(19,0), refund decimal(19,0), commission decimal(19,0), net decimal(19,0));
        INSERT @items
        SELECT order_id, total_amount, ticket_refund_amount,
               dbo.fn_CalculateCommission(remaining_ticket_amount, @rate, @fixed),
               remaining_ticket_amount - dbo.fn_CalculateCommission(remaining_ticket_amount, @rate, @fixed)
        FROM dbo.vw_OrderFinancialSummary WHERE event_id = @event_id AND status = ''PAID'';
        SELECT @gross = COALESCE(SUM(gross), 0), @refund = COALESCE(SUM(refund), 0),
               @commission = COALESCE(SUM(commission), 0), @net = COALESCE(SUM(net), 0) FROM @items;
        IF NOT EXISTS (SELECT 1 FROM @items) AND @settlement_id IS NULL BEGIN
            IF @started = 1 COMMIT TRANSACTION;
            SELECT CAST(NULL AS uniqueidentifier) settlement_id, @gross gross_revenue, @refund total_refund, @commission total_commission, @net net_payable; RETURN;
        END;
        IF @settlement_id IS NULL BEGIN SET @settlement_id = NEWID(); INSERT dbo.tc_settlements(id, event_id) VALUES (@settlement_id, @event_id); END;
        DELETE FROM dbo.tc_settlement_items WHERE settlement_id = @settlement_id;
        INSERT dbo.tc_settlement_items(settlement_id, order_id, gross_amount, refund_amount, commission_amount, net_amount)
        SELECT @settlement_id, order_id, gross, refund, commission, net FROM @items;
        UPDATE dbo.tc_settlements SET gross_revenue = @gross, total_refund = @refund,
               total_commission = @commission, net_payable = @net, version = version + 1 WHERE id = @settlement_id;
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @settlement_id settlement_id, @gross gross_revenue, @refund total_refund, @commission total_commission, @net net_payable;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp14;
        THROW;
    END CATCH
END;
');

EXEC(N'
CREATE OR ALTER PROCEDURE dbo.usp_ConfirmEventSettlement @settlement_id uniqueidentifier, @actor_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp15;
    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = ''ADMIN'')
            THROW 51550, ''Administrator role required'', 1;
        DECLARE @event_id uniqueidentifier, @status varchar(20), @net decimal(19,0);
        SELECT @event_id = event_id, @status = status, @net = net_payable FROM dbo.tc_settlements WITH (UPDLOCK, HOLDLOCK) WHERE id = @settlement_id;
        IF @event_id IS NULL THROW 51551, ''Settlement not found'', 1;
        IF @status IN (''CONFIRMED'', ''PAID'') BEGIN
            SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT id FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE event_id = @event_id ORDER BY id;
        IF EXISTS (SELECT 1 FROM dbo.fn_GetSettlementBlockers(@event_id, SYSUTCDATETIME()))
            THROW 51552, ''Settlement has unresolved blockers'', 1;
        EXEC dbo.usp_CalculateEventSettlement @event_id, @actor_id;
        SELECT @net = net_payable FROM dbo.tc_settlements WHERE id = @settlement_id;
        UPDATE dbo.tc_settlements SET status = CASE WHEN @net = 0 THEN ''PAID'' ELSE ''CONFIRMED'' END,
               confirmed_at = SYSUTCDATETIME(), version = version + 1 WHERE id = @settlement_id;
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id)
        VALUES (@actor_id, ''SETTLEMENT_CONFIRMED'', ''SETTLEMENT'', @settlement_id);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp15;
        THROW;
    END CATCH
END;
');

EXEC(N'
CREATE OR ALTER PROCEDURE dbo.usp_ExecutePayout
    @settlement_id uniqueidentifier, @payout_id uniqueidentifier, @actor_id uniqueidentifier,
    @amount decimal(19,0), @reference varchar(100), @verified_result varchar(20)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp16;
    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = ''ADMIN'')
            THROW 51560, ''Administrator role required'', 1;
        IF @amount <= 0 OR @verified_result NOT IN (''PENDING'', ''SUCCEEDED'', ''FAILED'') THROW 51561, ''Invalid payout input'', 1;
        DECLARE @event_id uniqueidentifier, @settlement_status varchar(20), @net decimal(19,0), @current varchar(20),
                @paid decimal(19,0), @pending decimal(19,0);
        SELECT @event_id = event_id, @settlement_status = status, @net = net_payable
        FROM dbo.tc_settlements WITH (UPDLOCK, HOLDLOCK) WHERE id = @settlement_id;
        IF @event_id IS NULL THROW 51562, ''Settlement not found'', 1;
        SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT @current = status FROM dbo.tc_payouts WITH (UPDLOCK, HOLDLOCK) WHERE id = @payout_id;
        IF @current = ''SUCCEEDED'' BEGIN
            SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @current IS NULL BEGIN
            IF @settlement_status <> ''CONFIRMED'' THROW 51563, ''Only a confirmed settlement accepts a payout'', 1;
            SELECT @paid = COALESCE(SUM(CASE WHEN status = ''SUCCEEDED'' THEN amount ELSE 0 END), 0),
                   @pending = COALESCE(SUM(CASE WHEN status = ''PENDING'' THEN amount ELSE 0 END), 0)
            FROM dbo.tc_payouts WITH (UPDLOCK, HOLDLOCK) WHERE settlement_id = @settlement_id;
            IF @paid + @pending + @amount > @net THROW 51564, ''Payout exceeds available settlement balance'', 1;
            INSERT dbo.tc_payouts(id, settlement_id, amount, reference, status, paid_at)
            VALUES (@payout_id, @settlement_id, @amount, @reference, @verified_result,
                    CASE WHEN @verified_result = ''SUCCEEDED'' THEN SYSUTCDATETIME() END);
        END ELSE BEGIN
            IF @current <> ''PENDING'' THROW 51565, ''Failed payout requires a new payout id'', 1;
            IF EXISTS (SELECT 1 FROM dbo.tc_payouts WHERE id = @payout_id AND (amount <> @amount OR reference <> @reference))
                THROW 51566, ''Payout replay payload mismatch'', 1;
            UPDATE dbo.tc_payouts SET status = @verified_result,
                   paid_at = CASE WHEN @verified_result = ''SUCCEEDED'' THEN SYSUTCDATETIME() ELSE NULL END,
                   version = version + 1 WHERE id = @payout_id;
        END;
        IF (SELECT COALESCE(SUM(amount), 0) FROM dbo.tc_payouts WHERE settlement_id = @settlement_id AND status = ''SUCCEEDED'') = @net
            UPDATE dbo.tc_settlements SET status = ''PAID'', version = version + 1 WHERE id = @settlement_id AND status = ''CONFIRMED'';
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id, detail)
        VALUES (@actor_id, CONCAT(''PAYOUT_'', @verified_result), ''PAYOUT'', @payout_id, @reference);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp16;
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
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id AND status = ''CANCELLED'')
            THROW 51570, ''Event is not cancelled'', 1;
        DECLARE @hold_id uniqueidentifier, @order_status varchar(30), @owner_id uniqueidentifier, @request_id uniqueidentifier;
        SELECT @hold_id = hold_id, @order_status = status, @owner_id = user_id
        FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE id = @order_id AND event_id = @event_id;
        IF @hold_id IS NULL THROW 51571, ''Order does not belong to cancelled event'', 1;
        IF @order_status IN (''PENDING_PAYMENT'', ''EXPIRED'', ''CANCELLED'') BEGIN
            IF EXISTS (SELECT 1 FROM dbo.tc_ticket_holds WHERE id = @hold_id AND status = ''ACTIVE'')
                EXEC dbo.usp_ReleaseTicketHold @hold_id, NULL, ''EVENT_CANCELLED'';
            IF @started = 1 COMMIT TRANSACTION;
            SELECT @order_id order_id, CAST(''RELEASED'' AS varchar(30)) result; RETURN;
        END;
        SELECT t.id FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK) JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
        WHERE oi.order_id = @order_id ORDER BY t.id;
        INSERT dbo.tc_audit_logs(action, aggregate_type, aggregate_id, detail)
        SELECT ''CANCELLED_EVENT_USED_TICKET'', ''TICKET'', t.id, CONCAT(N''{"orderId":"'', @order_id, N''"}'')
        FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
        WHERE oi.order_id = @order_id AND t.status = ''USED''
          AND NOT EXISTS (SELECT 1 FROM dbo.tc_audit_logs a WHERE a.action = ''CANCELLED_EVENT_USED_TICKET'' AND a.aggregate_id = t.id);
        SELECT TOP (1) @request_id = rr.id FROM dbo.tc_refund_requests rr
        WHERE rr.order_id = @order_id AND rr.status IN (''PENDING'', ''APPROVED'') ORDER BY rr.requested_at;
        IF @request_id IS NULL AND EXISTS (
            SELECT 1 FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
            WHERE oi.order_id = @order_id AND t.status IN (''ACTIVE'', ''REFUND_PENDING''))
        BEGIN
            SET @request_id = NEWID();
            INSERT dbo.tc_refund_requests(id, order_id, requester_id, reason, reason_type)
            VALUES (@request_id, @order_id, @owner_id, N''Event cancelled'', ''EVENT_CANCELLATION'');
            INSERT dbo.tc_refund_request_tickets(refund_request_id, ticket_id)
            SELECT @request_id, t.id FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
            WHERE oi.order_id = @order_id AND t.status = ''ACTIVE'';
            UPDATE t SET status = ''REFUND_PENDING'', version = version + 1 FROM dbo.tc_tickets t
            JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id WHERE oi.order_id = @order_id AND t.status = ''ACTIVE'';
        END ELSE IF @request_id IS NOT NULL
            UPDATE dbo.tc_refund_requests SET reason_type = ''EVENT_CANCELLATION'', version = version + 1 WHERE id = @request_id AND reason_type = ''CUSTOMER_REQUEST'';
        IF @request_id IS NOT NULL EXEC dbo.usp_ReviewRefundRequest @request_id, NULL, ''APPROVE'', NULL, 0;
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @order_id order_id, @request_id refund_request_id, CAST(''PROCESSED'' AS varchar(30)) result;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp17;
        THROW;
    END CATCH
END;
');
