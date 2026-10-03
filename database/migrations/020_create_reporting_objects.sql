SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC(N'
CREATE OR ALTER VIEW dbo.vw_EventSalesReport
AS
WITH orders AS (
    SELECT s.event_id, COUNT_BIG(*) paid_order_count,
           SUM(s.ticket_gross_amount) gross_revenue,
           SUM(s.successful_ticket_refund_amount) total_refund,
           SUM(dbo.fn_CalculateCommission(s.remaining_ticket_amount, r.rate_percent, r.fixed_fee)) projected_commission,
           SUM(s.remaining_ticket_amount - dbo.fn_CalculateCommission(s.remaining_ticket_amount, r.rate_percent, r.fixed_fee)) projected_net
    FROM dbo.vw_OrderFinancialSummary s
    JOIN dbo.tc_events e ON e.id = s.event_id
    JOIN dbo.tc_commission_rules r ON r.id = e.commission_rule_id
    WHERE s.status = ''PAID''
    GROUP BY s.event_id
), tickets AS (
    SELECT o.event_id,
           SUM(CASE WHEN t.status = ''ACTIVE'' THEN 1 ELSE 0 END) active_tickets,
           SUM(CASE WHEN t.status = ''USED'' THEN 1 ELSE 0 END) used_tickets,
           SUM(CASE WHEN t.status IN (''REFUNDED'', ''INVALIDATED'') THEN 1 ELSE 0 END) inactive_tickets
    FROM dbo.tc_tickets t
    JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
    JOIN dbo.tc_orders o ON o.id = oi.order_id
    GROUP BY o.event_id
)
SELECT e.id event_id, e.organization_id, e.title, e.status,
       COALESCE(o.paid_order_count, 0) paid_order_count,
       COALESCE(t.active_tickets, 0) active_tickets,
       COALESCE(t.used_tickets, 0) used_tickets,
       COALESCE(t.inactive_tickets, 0) inactive_tickets,
       CASE WHEN s.status IN (''CONFIRMED'', ''PAID'') THEN s.gross_revenue ELSE COALESCE(o.gross_revenue, 0) END gross_revenue,
       CASE WHEN s.status IN (''CONFIRMED'', ''PAID'') THEN s.total_refund ELSE COALESCE(o.total_refund, 0) END total_refund,
       CASE WHEN s.status IN (''CONFIRMED'', ''PAID'') THEN s.total_commission ELSE COALESCE(o.projected_commission, 0) END total_commission,
       CASE WHEN s.status IN (''CONFIRMED'', ''PAID'') THEN s.net_payable ELSE COALESCE(o.projected_net, 0) END net_payable,
       CASE WHEN s.status IN (''CONFIRMED'', ''PAID'') THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END is_settlement_snapshot
FROM dbo.tc_events e
LEFT JOIN orders o ON o.event_id = e.id
LEFT JOIN tickets t ON t.event_id = e.id
LEFT JOIN dbo.tc_settlements s ON s.event_id = e.id;
');

EXEC(N'
CREATE OR ALTER FUNCTION dbo.fn_GetOrganizationRevenue(
    @organization_id uniqueidentifier, @from_utc datetime2(3), @to_utc datetime2(3)
)
RETURNS TABLE
AS RETURN (
    SELECT COUNT_BIG(*) paid_order_count,
           COALESCE(SUM(s.ticket_gross_amount), 0) gross_revenue,
           COALESCE(SUM(COALESCE(r.refunded_amount, 0)), 0) refunded_amount,
           COALESCE(SUM(s.ticket_gross_amount - COALESCE(r.refunded_amount, 0)), 0) net_revenue
    FROM dbo.vw_OrderFinancialSummary s
    OUTER APPLY (
        SELECT SUM(x.amount) refunded_amount
        FROM dbo.tc_refunds x
        JOIN dbo.tc_payments p ON p.id = x.payment_id
        WHERE p.order_id = s.order_id AND x.purpose = ''CUSTOMER_REFUND''
          AND x.status = ''SUCCEEDED'' AND x.processed_at < @to_utc
    ) r
    WHERE s.organization_id = @organization_id AND s.status = ''PAID''
      AND s.paid_at >= @from_utc AND s.paid_at < @to_utc
);
');

EXEC(N'
CREATE OR ALTER FUNCTION dbo.fn_GetOrganizationCashFlow(
    @organization_id uniqueidentifier, @from_utc datetime2(3), @to_utc datetime2(3)
)
RETURNS TABLE
AS RETURN (
    WITH flows AS (
        SELECT CASE WHEN EXISTS (
                        SELECT 1 FROM dbo.tc_refunds r
                        WHERE r.payment_id = p.id AND r.purpose = ''PAYMENT_COMPENSATION''
                    ) THEN ''COMPENSATION_CAPTURE'' ELSE ''TICKET_CAPTURE'' END flow_type,
               p.amount inflow, CAST(0 AS decimal(19,0)) outflow
        FROM dbo.tc_payments p
        JOIN dbo.tc_orders o ON o.id = p.order_id
        JOIN dbo.tc_events e ON e.id = o.event_id
        WHERE e.organization_id = @organization_id AND p.status = ''CAPTURED''
          AND o.paid_at >= @from_utc AND o.paid_at < @to_utc
        UNION ALL
        SELECT CASE WHEN r.purpose = ''PAYMENT_COMPENSATION'' THEN ''COMPENSATION_REFUND'' ELSE ''TICKET_REFUND'' END,
               CAST(0 AS decimal(19,0)), r.amount
        FROM dbo.tc_refunds r
        JOIN dbo.tc_payments p ON p.id = r.payment_id
        JOIN dbo.tc_orders o ON o.id = p.order_id
        JOIN dbo.tc_events e ON e.id = o.event_id
        WHERE e.organization_id = @organization_id AND r.status = ''SUCCEEDED''
          AND r.processed_at >= @from_utc AND r.processed_at < @to_utc
    )
    SELECT flow_type, SUM(inflow) inflow_amount, SUM(outflow) outflow_amount,
           SUM(inflow - outflow) net_amount
    FROM flows GROUP BY flow_type
);
');

GRANT SELECT ON dbo.vw_EventSalesReport TO tc_manager;
GRANT SELECT ON dbo.vw_EventSalesReport TO tc_platform_admin;
GRANT SELECT ON OBJECT::dbo.fn_GetOrganizationRevenue TO tc_manager;
GRANT SELECT ON OBJECT::dbo.fn_GetOrganizationRevenue TO tc_platform_admin;
GRANT SELECT ON OBJECT::dbo.fn_GetOrganizationCashFlow TO tc_manager;
GRANT SELECT ON OBJECT::dbo.fn_GetOrganizationCashFlow TO tc_platform_admin;
