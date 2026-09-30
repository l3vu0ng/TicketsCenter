SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC(N'
CREATE OR ALTER TRIGGER dbo.trg_Zone_ProtectPublishedLayout ON dbo.tc_zones
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id = i.id
        JOIN dbo.tc_events e ON e.id = i.event_id
        WHERE e.status IN (''PUBLISHED'', ''CANCELLED'')
          AND (d.id IS NULL OR d.event_id <> i.event_id OR d.name <> i.name OR d.type <> i.type
               OR ISNULL(d.capacity, -1) <> ISNULL(i.capacity, -1))
    ) OR EXISTS (
        SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id = d.id
        JOIN dbo.tc_events e ON e.id = d.event_id
        WHERE e.status IN (''PUBLISHED'', ''CANCELLED'')
          AND (i.id IS NULL OR i.event_id <> d.event_id)
    ) THROW 51101, ''Published event zone layout is immutable'', 1;
END;
');

EXEC(N'
CREATE OR ALTER TRIGGER dbo.trg_Seat_ProtectPublishedLayout ON dbo.tc_seats
AFTER INSERT, UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.id = i.id
        JOIN dbo.tc_zones z ON z.id = i.zone_id JOIN dbo.tc_events e ON e.id = z.event_id
        WHERE e.status IN (''PUBLISHED'', ''CANCELLED'')
          AND (d.id IS NULL OR d.zone_id <> i.zone_id OR d.row_name <> i.row_name
               OR d.seat_number <> i.seat_number OR d.label <> i.label)
    ) OR EXISTS (
        SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id = d.id
        JOIN dbo.tc_zones z ON z.id = d.zone_id JOIN dbo.tc_events e ON e.id = z.event_id
        WHERE e.status IN (''PUBLISHED'', ''CANCELLED'') AND (i.id IS NULL OR i.zone_id <> d.zone_id)
    ) THROW 51102, ''Published event seat layout is immutable'', 1;
END;
');

EXEC(N'
CREATE OR ALTER TRIGGER dbo.trg_CommissionRule_ProtectAppliedTerms ON dbo.tc_commission_rules
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id = d.id
        WHERE EXISTS (SELECT 1 FROM dbo.tc_events e WHERE e.commission_rule_id = d.id)
          AND (i.id IS NULL OR i.organization_id <> d.organization_id
               OR i.rate_percent <> d.rate_percent OR i.fixed_fee <> d.fixed_fee)
    ) THROW 51103, ''Applied commission terms are immutable'', 1;
END;
');

EXEC(N'
CREATE OR ALTER TRIGGER dbo.trg_SettlementItem_ProtectConfirmedAmounts ON dbo.tc_settlement_items
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
    ) THROW 51104, ''Confirmed settlement items are immutable'', 1;
END;
');

EXEC(N'
CREATE OR ALTER TRIGGER dbo.trg_Event_AuditStatusChange ON dbo.tc_events
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id, detail)
    SELECT TRY_CONVERT(uniqueidentifier, SESSION_CONTEXT(N''actor_id'')), ''EVENT_STATUS_CHANGED'', ''EVENT'',
           i.id, CONCAT(N''{"from":"'', d.status, N''","to":"'', i.status, N''"}'')
    FROM inserted i JOIN deleted d ON d.id = i.id WHERE i.status <> d.status;
END;
');

EXEC(N'
CREATE OR ALTER TRIGGER dbo.trg_Membership_ProtectLastManager ON dbo.tc_organization_memberships
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM (SELECT organization_id FROM deleted UNION SELECT organization_id FROM inserted) x
        WHERE NOT EXISTS (
            SELECT 1 FROM dbo.tc_organization_memberships m WITH (UPDLOCK, HOLDLOCK)
            JOIN dbo.tc_users u ON u.id = m.user_id
            WHERE m.organization_id = x.organization_id AND m.role = ''MANAGER''
              AND m.active = 1 AND u.status = ''ACTIVE''
        )
    ) THROW 51106, ''Organization must retain an active manager'', 1;
END;
');

EXEC(N'
CREATE OR ALTER TRIGGER dbo.trg_Coupon_ProtectUsageLimit ON dbo.tc_coupons
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i
        WHERE i.max_uses < (SELECT COUNT_BIG(*) FROM dbo.tc_coupon_redemptions r WITH (UPDLOCK, HOLDLOCK)
                            WHERE r.coupon_id = i.id AND r.status IN (''RESERVED'', ''CONSUMED''))
    ) THROW 51107, ''Coupon max uses is below current usage'', 1;
END;
');

EXEC(N'
CREATE OR ALTER TRIGGER dbo.trg_Ticket_ProtectIssuedSnapshot ON dbo.tc_tickets
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i JOIN deleted d ON d.id = i.id
        WHERE i.order_item_id <> d.order_item_id OR i.ticket_code <> d.ticket_code
           OR i.qr_secret_hash <> d.qr_secret_hash OR i.paid_amount <> d.paid_amount
           OR i.issued_at <> d.issued_at
    ) THROW 51108, ''Issued ticket snapshot is immutable'', 1;
END;
');

EXEC(N'
CREATE OR ALTER TRIGGER dbo.trg_Settlement_ProtectConfirmedSnapshot ON dbo.tc_settlements
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
               OR NOT (d.status = ''CONFIRMED'' AND i.status IN (''CONFIRMED'', ''PAID'') OR d.status = ''PAID'' AND i.status = ''PAID''))
    ) THROW 51109, ''Confirmed settlement snapshot is immutable'', 1;
END;
');

EXEC(N'
CREATE OR ALTER TRIGGER dbo.trg_AuditLog_AppendOnly ON dbo.tc_audit_logs
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted) THROW 51110, ''Audit log is append-only'', 1;
END;
');
