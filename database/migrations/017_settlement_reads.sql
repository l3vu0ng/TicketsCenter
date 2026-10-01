SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC(N'
CREATE OR ALTER VIEW dbo.vw_OrderFinancialSummary
AS
WITH captured AS (
    SELECT order_id, SUM(amount) captured_amount
    FROM dbo.tc_payments
    WHERE status = ''CAPTURED''
    GROUP BY order_id
), refunds AS (
    SELECT p.order_id,
           SUM(CASE WHEN r.purpose = ''CUSTOMER_REFUND'' THEN r.amount ELSE 0 END) ticket_refund_amount,
           SUM(CASE WHEN r.purpose = ''PAYMENT_COMPENSATION'' THEN r.amount ELSE 0 END) compensation_refund_amount
    FROM dbo.tc_refunds r
    JOIN dbo.tc_payments p ON p.id = r.payment_id
    WHERE r.status = ''SUCCEEDED''
    GROUP BY p.order_id
), tickets AS (
    SELECT oi.order_id, SUM(t.paid_amount) ticket_gross_amount
    FROM dbo.tc_tickets t
    JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
    GROUP BY oi.order_id
)
SELECT o.id order_id, o.user_id owner_id, o.event_id, e.organization_id, o.status,
       o.subtotal_amount, o.discount_amount, o.total_amount, o.paid_at,
       COALESCE(c.captured_amount, 0) captured_amount,
       COALESCE(t.ticket_gross_amount, 0) issued_ticket_amount,
       COALESCE(t.ticket_gross_amount, 0) ticket_gross_amount,
       COALESCE(r.ticket_refund_amount, 0) ticket_refund_amount,
       COALESCE(r.ticket_refund_amount, 0) successful_ticket_refund_amount,
       COALESCE(r.compensation_refund_amount, 0) compensation_refund_amount,
       CASE WHEN COALESCE(t.ticket_gross_amount, 0) > COALESCE(r.ticket_refund_amount, 0)
            THEN COALESCE(t.ticket_gross_amount, 0) - COALESCE(r.ticket_refund_amount, 0)
            ELSE 0 END remaining_ticket_amount
FROM dbo.tc_orders o
JOIN dbo.tc_events e ON e.id = o.event_id
LEFT JOIN captured c ON c.order_id = o.id
LEFT JOIN refunds r ON r.order_id = o.id
LEFT JOIN tickets t ON t.order_id = o.id;
');

EXEC(N'
CREATE OR ALTER FUNCTION dbo.fn_CalculateCommission(
    @remaining_amount decimal(19,0), @rate_percent decimal(7,4), @fixed_fee decimal(19,0)
)
RETURNS decimal(19,0)
AS
BEGIN
    IF @remaining_amount IS NULL OR @rate_percent IS NULL OR @fixed_fee IS NULL
       OR @remaining_amount < 0 OR @rate_percent < 0 OR @fixed_fee < 0 RETURN NULL;
    IF @remaining_amount = 0 RETURN 0;
    DECLARE @fee decimal(19,0) = ROUND(@remaining_amount * @rate_percent / 100.0 + @fixed_fee, 0);
    RETURN CASE WHEN @fee < @remaining_amount THEN @fee ELSE @remaining_amount END;
END;
');

EXEC(N'
CREATE OR ALTER FUNCTION dbo.fn_GetSettlementBlockers(@event_id uniqueidentifier, @now_utc datetime2(3))
RETURNS TABLE
AS RETURN (
    SELECT CAST(''EVENT_NOT_ENDED'' AS varchar(40)) blocker_type, e.id blocker_id,
           CAST(NULL AS uniqueidentifier) related_id
    FROM dbo.tc_events e
    WHERE e.id = @event_id AND @now_utc < e.end_time

    UNION ALL
    SELECT ''PAYMENT_UNRESOLVED'', p.id, p.order_id
    FROM dbo.tc_payments p
    JOIN dbo.tc_orders o ON o.id = p.order_id
    WHERE o.event_id = @event_id AND p.status IN (''PENDING'', ''UNKNOWN'')

    UNION ALL
    SELECT ''REFUND_REQUEST_OPEN'', rr.id, rr.order_id
    FROM dbo.tc_refund_requests rr
    JOIN dbo.tc_orders o ON o.id = rr.order_id
    WHERE o.event_id = @event_id AND rr.status IN (''PENDING'', ''APPROVED'')

    UNION ALL
    SELECT ''REFUND_UNRESOLVED'', r.id, p.order_id
    FROM dbo.tc_refunds r
    JOIN dbo.tc_payments p ON p.id = r.payment_id
    JOIN dbo.tc_orders o ON o.id = p.order_id
    WHERE o.event_id = @event_id AND r.status IN (''PENDING'', ''UNKNOWN'')

    UNION ALL
    SELECT ''COMPENSATION_UNRESOLVED'', p.id, p.order_id
    FROM dbo.tc_payments p
    JOIN dbo.tc_orders o ON o.id = p.order_id
    WHERE o.event_id = @event_id AND p.status = ''CAPTURED''
      AND EXISTS (SELECT 1 FROM dbo.tc_refunds r WHERE r.payment_id = p.id AND r.purpose = ''PAYMENT_COMPENSATION'')
      AND COALESCE((SELECT SUM(r.amount) FROM dbo.tc_refunds r
                    WHERE r.payment_id = p.id AND r.purpose = ''PAYMENT_COMPENSATION''
                      AND r.status = ''SUCCEEDED''), 0) < p.amount

    UNION ALL
    SELECT ''CANCELLATION_PENDING'', x.id, x.aggregate_id
    FROM dbo.tc_outbox x
    WHERE x.aggregate_type = ''EVENT'' AND x.aggregate_id = @event_id
      AND x.event_type = ''EVENT_CANCELLED'' AND x.status <> ''PUBLISHED''
);
');

GRANT SELECT ON dbo.vw_OrderFinancialSummary TO tc_buyer;
GRANT SELECT ON dbo.vw_OrderFinancialSummary TO tc_manager;
GRANT SELECT ON dbo.vw_OrderFinancialSummary TO tc_platform_admin;
GRANT SELECT ON OBJECT::dbo.fn_GetSettlementBlockers TO tc_platform_admin;
