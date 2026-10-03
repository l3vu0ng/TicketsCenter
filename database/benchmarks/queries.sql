-- Execute in a disposable TicketsCenter_Benchmark database with actual plans
-- and SET STATISTICS IO, TIME ON. Parameters are deliberately fixed per run.
SET NOCOUNT ON;
SET STATISTICS IO ON;
SET STATISTICS TIME ON;
DECLARE @event uniqueidentifier = '00000000-0000-0000-0000-000000000001',
        @org uniqueidentifier = '00000000-0000-0000-0000-000000000002',
        @user uniqueidentifier = '00000000-0000-0000-0000-000000000003',
        @zone uniqueidentifier = '00000000-0000-0000-0000-000000000004',
        @settlement uniqueidentifier = '00000000-0000-0000-0000-000000000005',
        @aggregate uniqueidentifier = '00000000-0000-0000-0000-000000000006',
        @from datetime2(3) = '2026-01-01', @to datetime2(3) = '2027-01-01';

-- IX01..IX15: catalog, zone, buyer orders, payments, check-ins, expired holds,
-- event orders, refunds, coupon use, refund queue, tickets, payouts, audit,
-- commission rules, and organization event list.
SELECT TOP (50) * FROM dbo.vw_PublicEvents WHERE start_time >= @from AND start_time < @to ORDER BY start_time, event_id;
SELECT * FROM dbo.vw_ZoneInventory WHERE zone_id = @zone;
SELECT TOP (50) * FROM dbo.tc_orders WHERE user_id = @user ORDER BY created_at DESC, id;
SELECT TOP (100) * FROM dbo.tc_payments WHERE status IN ('PENDING','UNKNOWN') ORDER BY created_at, id;
SELECT TOP (100) * FROM dbo.vw_CheckInHistory WHERE event_id = @event ORDER BY scanned_at DESC, id;
SELECT TOP (100) * FROM dbo.tc_ticket_holds WHERE status = 'ACTIVE' AND expires_at < SYSUTCDATETIME() ORDER BY expires_at, id;
SELECT * FROM dbo.vw_OrderFinancialSummary WHERE event_id = @event AND status = 'PAID';
SELECT TOP (100) * FROM dbo.tc_refunds WHERE status IN ('PENDING','UNKNOWN') ORDER BY created_at, id;
SELECT * FROM dbo.tc_coupon_redemptions WHERE status IN ('RESERVED','CONSUMED') ORDER BY order_id;
SELECT TOP (100) * FROM dbo.vw_RefundRequestOverview WHERE status = 'PENDING' ORDER BY requested_at, request_id;
SELECT t.* FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id WHERE oi.order_id IN (SELECT TOP (20) id FROM dbo.tc_orders WHERE user_id = @user) ORDER BY t.id;
SELECT * FROM dbo.tc_payouts WHERE settlement_id = @settlement ORDER BY status, id;
SELECT TOP (100) * FROM dbo.tc_audit_logs WHERE aggregate_type = 'EVENT' AND aggregate_id = @aggregate ORDER BY created_at DESC, id;
SELECT * FROM dbo.tc_commission_rules WHERE organization_id = @org AND effective_from <= SYSUTCDATETIME() ORDER BY effective_from DESC, id;
SELECT TOP (100) * FROM dbo.tc_events WHERE organization_id = @org AND status IN ('DRAFT','PUBLISHED') ORDER BY start_time, id;
