SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC(N'
CREATE OR ALTER PROCEDURE dbo.usp_RecalculateSettlement
    @event_id uniqueidentifier,
    @actor_id uniqueidentifier,
    @return_result bit = 1
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp14;
    BEGIN TRY
        IF @event_id IS NULL OR @actor_id IS NULL THROW 51700, ''Event and actor are required'', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = ''ADMIN'')
            THROW 51701, ''Administrator role required'', 1;

        DECLARE @rule_id uniqueidentifier, @rate decimal(7,4), @fixed decimal(19,0),
                @settlement_id uniqueidentifier, @settlement_status varchar(20), @lock_count bigint,
                @gross decimal(19,0), @refund decimal(19,0), @commission decimal(19,0), @net decimal(19,0);
        SELECT @rule_id = commission_rule_id
        FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK)
        WHERE id = @event_id;
        IF @rule_id IS NULL THROW 51702, ''Event not found or has no applied commission rule'', 1;
        SELECT @rate = rate_percent, @fixed = fixed_fee
        FROM dbo.tc_commission_rules WITH (HOLDLOCK)
        WHERE id = @rule_id;
        IF dbo.fn_CalculateCommission(0, @rate, @fixed) IS NULL THROW 51703, ''Applied commission rule is invalid'', 1;

        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE event_id = @event_id;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_refund_requests rr WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_orders o ON o.id = rr.order_id WHERE o.event_id = @event_id;
        SELECT @settlement_id = id, @settlement_status = status
        FROM dbo.tc_settlements WITH (UPDLOCK, HOLDLOCK)
        WHERE event_id = @event_id;
        IF @settlement_status IN (''CONFIRMED'', ''PAID'')
            THROW 51704, ''Confirmed settlement cannot be recalculated'', 1;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_payments p WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_orders o ON o.id = p.order_id WHERE o.event_id = @event_id;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_refunds r WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_payments p ON p.id = r.payment_id
        JOIN dbo.tc_orders o ON o.id = p.order_id WHERE o.event_id = @event_id;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
        JOIN dbo.tc_orders o ON o.id = oi.order_id WHERE o.event_id = @event_id;

        DECLARE @items TABLE(
            order_id uniqueidentifier PRIMARY KEY,
            gross decimal(19,0) NOT NULL,
            refund decimal(19,0) NOT NULL,
            commission decimal(19,0) NOT NULL,
            net decimal(19,0) NOT NULL
        );
        INSERT @items(order_id, gross, refund, commission, net)
        SELECT order_id, ticket_gross_amount, successful_ticket_refund_amount,
               dbo.fn_CalculateCommission(remaining_ticket_amount, @rate, @fixed),
               remaining_ticket_amount - dbo.fn_CalculateCommission(remaining_ticket_amount, @rate, @fixed)
        FROM dbo.vw_OrderFinancialSummary
        WHERE event_id = @event_id AND status = ''PAID'' AND ticket_gross_amount > 0;

        SELECT @gross = COALESCE(SUM(gross), 0), @refund = COALESCE(SUM(refund), 0),
               @commission = COALESCE(SUM(commission), 0), @net = COALESCE(SUM(net), 0)
        FROM @items;

        IF NOT EXISTS (SELECT 1 FROM @items) BEGIN
            IF @settlement_id IS NOT NULL BEGIN
                DELETE FROM dbo.tc_settlement_items WHERE settlement_id = @settlement_id;
                DELETE FROM dbo.tc_settlements WHERE id = @settlement_id AND status = ''DRAFT'';
            END;
            IF @started = 1 COMMIT TRANSACTION;
            IF @return_result = 1
                SELECT CAST(NULL AS uniqueidentifier) settlement_id, CAST(''EMPTY'' AS varchar(20)) status,
                       @gross gross_revenue, @refund total_refund, @commission total_commission, @net net_payable;
            RETURN;
        END;

        IF @settlement_id IS NULL BEGIN
            SET @settlement_id = NEWID();
            INSERT dbo.tc_settlements(id, event_id) VALUES (@settlement_id, @event_id);
        END;
        DELETE FROM dbo.tc_settlement_items WHERE settlement_id = @settlement_id;
        INSERT dbo.tc_settlement_items(settlement_id, order_id, gross_amount, refund_amount, commission_amount, net_amount)
        SELECT @settlement_id, order_id, gross, refund, commission, net FROM @items;
        UPDATE dbo.tc_settlements
        SET gross_revenue = @gross, total_refund = @refund, total_commission = @commission,
            net_payable = @net, version = version + 1
        WHERE id = @settlement_id AND status = ''DRAFT'';

        IF @started = 1 COMMIT TRANSACTION;
        IF @return_result = 1
            SELECT @settlement_id settlement_id, CAST(''DRAFT'' AS varchar(20)) status,
                   @gross gross_revenue, @refund total_refund, @commission total_commission, @net net_payable;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp14;
        THROW;
    END CATCH
END;
');

EXEC(N'
CREATE OR ALTER PROCEDURE dbo.usp_ConfirmSettlement
    @settlement_id uniqueidentifier,
    @actor_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp15;
    BEGIN TRY
        IF @settlement_id IS NULL OR @actor_id IS NULL THROW 51710, ''Settlement and actor are required'', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = ''ADMIN'')
            THROW 51711, ''Administrator role required'', 1;

        DECLARE @event_id uniqueidentifier, @status varchar(20), @net decimal(19,0), @lock_count bigint;
        SELECT @event_id = event_id FROM dbo.tc_settlements WHERE id = @settlement_id;
        IF @event_id IS NULL THROW 51712, ''Settlement not found'', 1;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT @lock_count = COUNT_BIG(*) FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE event_id = @event_id;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_refund_requests rr WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_orders o ON o.id = rr.order_id WHERE o.event_id = @event_id;
        SELECT @status = status, @net = net_payable
        FROM dbo.tc_settlements WITH (UPDLOCK, HOLDLOCK)
        WHERE id = @settlement_id AND event_id = @event_id;
        IF @status IN (''CONFIRMED'', ''PAID'') BEGIN
            IF @started = 1 COMMIT TRANSACTION;
            SELECT * FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id = @settlement_id;
            RETURN;
        END;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_payments p WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_orders o ON o.id = p.order_id WHERE o.event_id = @event_id;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_refunds r WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_payments p ON p.id = r.payment_id
        JOIN dbo.tc_orders o ON o.id = p.order_id WHERE o.event_id = @event_id;
        SELECT @lock_count = COUNT_BIG(*)
        FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
        JOIN dbo.tc_orders o ON o.id = oi.order_id WHERE o.event_id = @event_id;

        IF EXISTS (SELECT 1 FROM dbo.fn_GetSettlementBlockers(@event_id, SYSUTCDATETIME()))
            THROW 51713, ''Settlement has unresolved blockers'', 1;
        EXEC dbo.usp_RecalculateSettlement @event_id, @actor_id, 0;
        SELECT @net = net_payable FROM dbo.tc_settlements WHERE id = @settlement_id;
        UPDATE dbo.tc_settlements
        SET status = CASE WHEN @net = 0 THEN ''PAID'' ELSE ''CONFIRMED'' END,
            confirmed_at = SYSUTCDATETIME(), version = version + 1
        WHERE id = @settlement_id AND status = ''DRAFT'';
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
CREATE OR ALTER TRIGGER dbo.trg_SettlementItem_ProtectConfirmedAmounts
ON dbo.tc_settlement_items
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i JOIN dbo.tc_settlements s ON s.id = i.settlement_id
        WHERE s.status IN (''CONFIRMED'', ''PAID'')
    ) OR EXISTS (
        SELECT 1 FROM deleted d JOIN dbo.tc_settlements s ON s.id = d.settlement_id
        WHERE s.status IN (''CONFIRMED'', ''PAID'')
    ) THROW 51720, ''Confirmed settlement items are immutable'', 1;
END;
');

EXEC(N'
CREATE OR ALTER TRIGGER dbo.trg_Settlement_ProtectConfirmedSnapshot
ON dbo.tc_settlements
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id = d.id
        WHERE d.status IN (''CONFIRMED'', ''PAID'')
          AND (i.id IS NULL OR i.event_id <> d.event_id OR i.created_at <> d.created_at
               OR ISNULL(i.confirmed_at, ''19000101'') <> ISNULL(d.confirmed_at, ''19000101'')
               OR i.gross_revenue <> d.gross_revenue OR i.total_refund <> d.total_refund
               OR i.total_commission <> d.total_commission OR i.net_payable <> d.net_payable
               OR NOT (d.status = ''CONFIRMED'' AND i.status IN (''CONFIRMED'', ''PAID'')
                       OR d.status = ''PAID'' AND i.status = ''PAID''))
    ) THROW 51721, ''Confirmed settlement snapshot is immutable'', 1;
END;
');

GRANT EXECUTE ON dbo.usp_RecalculateSettlement TO tc_platform_admin;
GRANT EXECUTE ON dbo.usp_ConfirmSettlement TO tc_platform_admin;
