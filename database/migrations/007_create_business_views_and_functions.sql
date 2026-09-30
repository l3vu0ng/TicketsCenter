SET NOCOUNT ON;
SET XACT_ABORT ON;

-- Views aggregate each one-to-many relation before joining to avoid multiplying money.
EXEC(N'
CREATE OR ALTER VIEW dbo.vw_PublicEvents
AS
SELECT e.id event_id, e.organization_id, e.category_id, e.title, e.description,
       e.cover_image_url, e.venue_name, e.venue_address, e.sale_start, e.sale_end,
       e.start_time, e.end_time, MIN(z.price) minimum_price
FROM dbo.tc_events e
LEFT JOIN dbo.tc_zones z ON z.event_id = e.id
WHERE e.status = ''PUBLISHED''
GROUP BY e.id, e.organization_id, e.category_id, e.title, e.description,
         e.cover_image_url, e.venue_name, e.venue_address, e.sale_start, e.sale_end,
         e.start_time, e.end_time;
');

EXEC(N'
CREATE OR ALTER VIEW dbo.vw_ZoneInventory
AS
WITH seated AS (
    SELECT zone_id, COUNT_BIG(*) capacity,
           SUM(CASE WHEN status = ''HELD'' THEN 1 ELSE 0 END) held,
           SUM(CASE WHEN status = ''SOLD'' THEN 1 ELSE 0 END) sold,
           SUM(CASE WHEN status = ''AVAILABLE'' THEN 1 ELSE 0 END) available
    FROM dbo.tc_seats GROUP BY zone_id
)
SELECT z.id zone_id, z.event_id, e.organization_id, z.name, z.type, z.price,
       CASE WHEN z.type = ''SEATED'' THEN COALESCE(s.capacity, 0) ELSE z.capacity END capacity,
       CASE WHEN z.type = ''SEATED'' THEN COALESCE(s.held, 0) ELSE z.held_quantity END held_quantity,
       CASE WHEN z.type = ''SEATED'' THEN COALESCE(s.sold, 0) ELSE z.sold_quantity END sold_quantity,
       CASE WHEN z.type = ''SEATED'' THEN COALESCE(s.available, 0)
            ELSE z.capacity - z.held_quantity - z.sold_quantity END available_quantity
FROM dbo.tc_zones z
JOIN dbo.tc_events e ON e.id = z.event_id
LEFT JOIN seated s ON s.zone_id = z.id;
');

EXEC(N'
CREATE OR ALTER VIEW dbo.vw_OrderFinancialSummary
AS
WITH captured AS (
    SELECT order_id, SUM(amount) captured_amount
    FROM dbo.tc_payments WHERE status = ''CAPTURED'' GROUP BY order_id
), refunded AS (
    SELECT p.order_id,
           SUM(CASE WHEN r.purpose = ''CUSTOMER_REFUND'' THEN r.amount ELSE 0 END) ticket_refund_amount,
           SUM(CASE WHEN r.purpose = ''PAYMENT_COMPENSATION'' THEN r.amount ELSE 0 END) compensation_refund_amount
    FROM dbo.tc_refunds r JOIN dbo.tc_payments p ON p.id = r.payment_id
    WHERE r.status = ''SUCCEEDED'' GROUP BY p.order_id
), ticket_value AS (
    SELECT oi.order_id, SUM(t.paid_amount) issued_ticket_amount
    FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
    GROUP BY oi.order_id
)
SELECT o.id order_id, o.user_id owner_id, o.event_id, e.organization_id, o.status,
       o.subtotal_amount, o.discount_amount, o.total_amount, o.paid_at,
       COALESCE(c.captured_amount, 0) captured_amount,
       COALESCE(tv.issued_ticket_amount, 0) issued_ticket_amount,
       COALESCE(r.ticket_refund_amount, 0) ticket_refund_amount,
       COALESCE(r.compensation_refund_amount, 0) compensation_refund_amount,
       CASE WHEN o.total_amount > COALESCE(r.ticket_refund_amount, 0)
            THEN o.total_amount - COALESCE(r.ticket_refund_amount, 0) ELSE 0 END remaining_ticket_amount
FROM dbo.tc_orders o JOIN dbo.tc_events e ON e.id = o.event_id
LEFT JOIN captured c ON c.order_id = o.id
LEFT JOIN refunded r ON r.order_id = o.id
LEFT JOIN ticket_value tv ON tv.order_id = o.id;
');

EXEC(N'
CREATE OR ALTER VIEW dbo.vw_EventSalesReport
AS
WITH orders AS (
    SELECT event_id, COUNT_BIG(*) paid_order_count,
           SUM(total_amount) gross_revenue,
           SUM(ticket_refund_amount) total_refund,
           SUM(remaining_ticket_amount) remaining_revenue
    FROM dbo.vw_OrderFinancialSummary WHERE status = ''PAID'' GROUP BY event_id
), tickets AS (
    SELECT oi.order_id,
           SUM(CASE WHEN t.status = ''ACTIVE'' THEN 1 ELSE 0 END) active_tickets,
           SUM(CASE WHEN t.status = ''USED'' THEN 1 ELSE 0 END) used_tickets,
           SUM(CASE WHEN t.status IN (''REFUNDED'', ''INVALIDATED'') THEN 1 ELSE 0 END) inactive_tickets
    FROM dbo.tc_order_items oi JOIN dbo.tc_tickets t ON t.order_item_id = oi.id GROUP BY oi.order_id
), event_tickets AS (
    SELECT o.event_id, SUM(t.active_tickets) active_tickets, SUM(t.used_tickets) used_tickets,
           SUM(t.inactive_tickets) inactive_tickets
    FROM tickets t JOIN dbo.tc_orders o ON o.id = t.order_id GROUP BY o.event_id
)
SELECT e.id event_id, e.organization_id, e.title, e.status,
       COALESCE(o.paid_order_count, 0) paid_order_count,
       COALESCE(t.active_tickets, 0) active_tickets, COALESCE(t.used_tickets, 0) used_tickets,
       COALESCE(t.inactive_tickets, 0) inactive_tickets,
       COALESCE(o.gross_revenue, 0) gross_revenue, COALESCE(o.total_refund, 0) total_refund,
       COALESCE(s.total_commission, 0) total_commission,
       COALESCE(s.net_payable, o.remaining_revenue, 0) net_payable,
       CASE WHEN s.status IN (''CONFIRMED'', ''PAID'') THEN 1 ELSE 0 END is_settlement_snapshot
FROM dbo.tc_events e LEFT JOIN orders o ON o.event_id = e.id
LEFT JOIN event_tickets t ON t.event_id = e.id
LEFT JOIN dbo.tc_settlements s ON s.event_id = e.id;
');

EXEC(N'
CREATE OR ALTER VIEW dbo.vw_CheckInHistory
AS
SELECT ci.id check_in_id, ci.event_id, e.organization_id, ci.actor_id, ci.scanned_at,
       ci.result, ci.ticket_id, oi.zone_name_snapshot, oi.seat_label_snapshot
FROM dbo.tc_check_ins ci JOIN dbo.tc_events e ON e.id = ci.event_id
LEFT JOIN dbo.tc_tickets t ON t.id = ci.ticket_id
LEFT JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id;
');

EXEC(N'
CREATE OR ALTER VIEW dbo.vw_TicketDetails
AS
SELECT t.id ticket_id, o.user_id owner_id, o.id order_id, o.event_id, e.organization_id,
       e.title event_title, e.venue_name, e.venue_address, e.start_time, e.end_time,
       oi.zone_name_snapshot, oi.seat_label_snapshot, t.paid_amount, t.status, t.issued_at
FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
JOIN dbo.tc_orders o ON o.id = oi.order_id JOIN dbo.tc_events e ON e.id = o.event_id;
');

EXEC(N'
CREATE OR ALTER VIEW dbo.vw_RefundRequestOverview
AS
WITH requested AS (
    SELECT rt.refund_request_id, COUNT_BIG(*) ticket_count, SUM(t.paid_amount) requested_amount
    FROM dbo.tc_refund_request_tickets rt JOIN dbo.tc_tickets t ON t.id = rt.ticket_id
    GROUP BY rt.refund_request_id
), attempts AS (
    SELECT refund_request_id, SUM(CASE WHEN status = ''SUCCEEDED'' THEN amount ELSE 0 END) refunded_amount,
           SUM(CASE WHEN status = ''PENDING'' THEN 1 ELSE 0 END) pending_attempts,
           SUM(CASE WHEN status = ''UNKNOWN'' THEN 1 ELSE 0 END) unknown_attempts,
           SUM(CASE WHEN status = ''FAILED'' THEN 1 ELSE 0 END) failed_attempts
    FROM dbo.tc_refunds WHERE refund_request_id IS NOT NULL GROUP BY refund_request_id
)
SELECT rr.id refund_request_id, o.user_id owner_id, o.event_id, e.organization_id,
       rr.reason_type, rr.reason, rr.status, rr.requested_at, rr.decided_at,
       COALESCE(q.ticket_count, 0) ticket_count, COALESCE(q.requested_amount, 0) requested_amount,
       COALESCE(a.refunded_amount, 0) refunded_amount, COALESCE(a.pending_attempts, 0) pending_attempts,
       COALESCE(a.unknown_attempts, 0) unknown_attempts, COALESCE(a.failed_attempts, 0) failed_attempts
FROM dbo.tc_refund_requests rr JOIN dbo.tc_orders o ON o.id = rr.order_id
JOIN dbo.tc_events e ON e.id = o.event_id LEFT JOIN requested q ON q.refund_request_id = rr.id
LEFT JOIN attempts a ON a.refund_request_id = rr.id;
');

EXEC(N'
CREATE OR ALTER VIEW dbo.vw_SettlementPayoutBalance
AS
WITH p AS (
    SELECT settlement_id,
           SUM(CASE WHEN status = ''SUCCEEDED'' THEN amount ELSE 0 END) paid_amount,
           SUM(CASE WHEN status = ''PENDING'' THEN amount ELSE 0 END) pending_amount
    FROM dbo.tc_payouts GROUP BY settlement_id
)
SELECT s.id settlement_id, s.event_id, s.gross_revenue, s.total_refund, s.total_commission,
       s.net_payable, s.status, s.confirmed_at, COALESCE(p.paid_amount, 0) paid_amount,
       COALESCE(p.pending_amount, 0) pending_amount,
       CASE WHEN s.net_payable > COALESCE(p.paid_amount, 0)
            THEN s.net_payable - COALESCE(p.paid_amount, 0) ELSE 0 END remaining_amount,
       CASE WHEN s.net_payable > COALESCE(p.paid_amount, 0) + COALESCE(p.pending_amount, 0)
            THEN s.net_payable - COALESCE(p.paid_amount, 0) - COALESCE(p.pending_amount, 0) ELSE 0 END available_amount
FROM dbo.tc_settlements s LEFT JOIN p ON p.settlement_id = s.id;
');

EXEC(N'
CREATE OR ALTER VIEW dbo.vw_CouponUsage
AS
WITH u AS (
    SELECT coupon_id,
           SUM(CASE WHEN status = ''RESERVED'' THEN 1 ELSE 0 END) reserved_uses,
           SUM(CASE WHEN status = ''CONSUMED'' THEN 1 ELSE 0 END) consumed_uses,
           SUM(CASE WHEN status = ''RELEASED'' THEN 1 ELSE 0 END) released_uses
    FROM dbo.tc_coupon_redemptions GROUP BY coupon_id
)
SELECT c.id coupon_id, c.organization_id, c.code, c.active, c.valid_from, c.valid_to, c.max_uses,
       COALESCE(u.reserved_uses, 0) reserved_uses, COALESCE(u.consumed_uses, 0) consumed_uses,
       COALESCE(u.released_uses, 0) released_uses,
       CASE WHEN c.max_uses > COALESCE(u.reserved_uses, 0) + COALESCE(u.consumed_uses, 0)
            THEN c.max_uses - COALESCE(u.reserved_uses, 0) - COALESCE(u.consumed_uses, 0) ELSE 0 END available_uses
FROM dbo.tc_coupons c LEFT JOIN u ON u.coupon_id = c.id;
');

EXEC(N'
CREATE OR ALTER VIEW dbo.vw_OrganizationMembers
AS
SELECT m.id membership_id, m.organization_id, m.user_id, u.email, u.full_name,
       m.role, m.active, u.status user_status, m.joined_at
FROM dbo.tc_organization_memberships m JOIN dbo.tc_users u ON u.id = m.user_id;
');

EXEC(N'
CREATE OR ALTER FUNCTION dbo.fn_CalculateCouponDiscount(
    @subtotal decimal(19,0), @discount_type varchar(30),
    @percentage decimal(5,2), @fixed_amount decimal(19,0)
)
RETURNS decimal(19,0)
AS
BEGIN
    IF @subtotal IS NULL OR @subtotal < 0 RETURN NULL;
    DECLARE @cap decimal(19,0) = FLOOR(@subtotal * 0.30);
    IF @discount_type = ''PERCENTAGE'' AND @percentage > 0 AND @percentage <= 30 AND @fixed_amount IS NULL
        RETURN FLOOR(@subtotal * @percentage / 100.0);
    IF @discount_type = ''FIXED_AMOUNT'' AND @fixed_amount > 0 AND @percentage IS NULL
        RETURN CASE WHEN @fixed_amount < @cap THEN @fixed_amount ELSE @cap END;
    RETURN NULL;
END;
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
    DECLARE @fee decimal(19,0) = FLOOR(@remaining_amount * @rate_percent / 100.0 + @fixed_fee + 0.5);
    RETURN CASE WHEN @fee < @remaining_amount THEN @fee ELSE @remaining_amount END;
END;
');

EXEC(N'
CREATE OR ALTER FUNCTION dbo.fn_GetZoneAvailability(@zone_id uniqueidentifier)
RETURNS TABLE
AS RETURN (SELECT * FROM dbo.vw_ZoneInventory WHERE zone_id = @zone_id);
');

EXEC(N'
CREATE OR ALTER FUNCTION dbo.fn_GetRefundableTickets(@order_id uniqueidentifier, @now_utc datetime2(3))
RETURNS TABLE
AS RETURN (
    SELECT t.id ticket_id, t.paid_amount, oi.zone_name_snapshot, oi.seat_label_snapshot
    FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
    JOIN dbo.tc_orders o ON o.id = oi.order_id JOIN dbo.tc_events e ON e.id = o.event_id
    WHERE o.id = @order_id AND @now_utc < e.start_time AND t.status = ''ACTIVE''
      AND NOT EXISTS (SELECT 1 FROM dbo.tc_refund_request_tickets rt WHERE rt.ticket_id = t.id AND rt.is_open = 1)
);
');

EXEC(N'
CREATE OR ALTER FUNCTION dbo.fn_GetOrganizationRevenue(
    @organization_id uniqueidentifier, @from_utc datetime2(3), @to_utc datetime2(3)
)
RETURNS TABLE
AS RETURN (
    SELECT COUNT_BIG(*) paid_order_count, COALESCE(SUM(s.total_amount), 0) gross_revenue,
           COALESCE(SUM(COALESCE(r.refunded_amount, 0)), 0) refunded_amount,
           COALESCE(SUM(s.total_amount - COALESCE(r.refunded_amount, 0)), 0) net_revenue
    FROM dbo.vw_OrderFinancialSummary s
    OUTER APPLY (
        SELECT SUM(x.amount) refunded_amount FROM dbo.tc_refunds x
        JOIN dbo.tc_payments p ON p.id = x.payment_id
        WHERE p.order_id = s.order_id AND x.purpose = ''CUSTOMER_REFUND'' AND x.status = ''SUCCEEDED''
          AND x.processed_at < @to_utc
    ) r
    WHERE s.organization_id = @organization_id AND s.status = ''PAID''
      AND s.paid_at >= @from_utc AND s.paid_at < @to_utc
);
');

EXEC(N'
CREATE OR ALTER FUNCTION dbo.fn_AllocateTicketPaidAmounts(@order_id uniqueidentifier)
RETURNS TABLE
AS RETURN (
    WITH n AS (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
               UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8),
    units AS (
        SELECT oi.id order_item_id, n.n ticket_ordinal, oi.unit_price,
               o.subtotal_amount, o.discount_amount, o.total_amount,
               CAST(CASE WHEN o.subtotal_amount = 0 THEN 0
                    ELSE o.discount_amount * oi.unit_price / o.subtotal_amount END AS decimal(38,12)) raw_discount
        FROM dbo.tc_order_items oi JOIN dbo.tc_orders o ON o.id = oi.order_id
        JOIN n ON n.n <= oi.quantity WHERE o.id = @order_id
    ), base AS (
        SELECT *, FLOOR(raw_discount) base_discount, raw_discount - FLOOR(raw_discount) remainder,
               SUM(FLOOR(raw_discount)) OVER () allocated_base
        FROM units
    ), ranked AS (
        SELECT *, ROW_NUMBER() OVER (ORDER BY remainder DESC, order_item_id, ticket_ordinal) remainder_rank
        FROM base
    )
    SELECT order_item_id, ticket_ordinal, unit_price original_amount,
           CAST(base_discount + CASE WHEN remainder_rank <= discount_amount - allocated_base THEN 1 ELSE 0 END AS decimal(19,0)) discount_amount,
           CAST(unit_price - base_discount - CASE WHEN remainder_rank <= discount_amount - allocated_base THEN 1 ELSE 0 END AS decimal(19,0)) paid_amount
    FROM ranked
);
');

EXEC(N'
CREATE OR ALTER FUNCTION dbo.fn_GetEventCheckInWindow(@event_id uniqueidentifier, @now_utc datetime2(3))
RETURNS TABLE
AS RETURN (
    SELECT id event_id, DATEADD(minute, -60, start_time) opens_at, end_time closes_at,
           CASE WHEN status = ''CANCELLED'' THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END is_cancelled,
           CASE WHEN status <> ''CANCELLED'' AND @now_utc >= DATEADD(minute, -60, start_time)
                     AND @now_utc < end_time THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END is_open
    FROM dbo.tc_events WHERE id = @event_id
);
');

EXEC(N'
CREATE OR ALTER FUNCTION dbo.fn_GetCouponEligibility(
    @coupon_id uniqueidentifier, @organization_id uniqueidentifier,
    @subtotal decimal(19,0), @now_utc datetime2(3)
)
RETURNS TABLE
AS RETURN (
    SELECT c.id coupon_id,
       CASE WHEN c.organization_id <> @organization_id THEN CAST(0 AS bit)
            WHEN c.active = 0 THEN CAST(0 AS bit)
            WHEN @now_utc < c.valid_from OR @now_utc >= c.valid_to THEN CAST(0 AS bit)
            WHEN u.available_uses <= 0 THEN CAST(0 AS bit) ELSE CAST(1 AS bit) END is_eligible,
       CASE WHEN c.organization_id <> @organization_id THEN ''WRONG_ORGANIZATION''
            WHEN c.active = 0 THEN ''INACTIVE''
            WHEN @now_utc < c.valid_from THEN ''NOT_STARTED'' WHEN @now_utc >= c.valid_to THEN ''EXPIRED''
            WHEN u.available_uses <= 0 THEN ''QUOTA_EXHAUSTED'' ELSE ''ELIGIBLE'' END reason,
       u.available_uses,
       dbo.fn_CalculateCouponDiscount(@subtotal, c.discount_type, c.percentage_value, c.fixed_amount) preview_discount
    FROM dbo.tc_coupons c JOIN dbo.vw_CouponUsage u ON u.coupon_id = c.id WHERE c.id = @coupon_id
);
');

EXEC(N'
CREATE OR ALTER FUNCTION dbo.fn_GetSettlementBlockers(@event_id uniqueidentifier, @now_utc datetime2(3))
RETURNS TABLE
AS RETURN (
    SELECT CAST(''EVENT_NOT_ENDED'' AS varchar(40)) blocker_type, e.id blocker_id, CAST(NULL AS uniqueidentifier) related_id
    FROM dbo.tc_events e WHERE e.id = @event_id AND @now_utc < e.end_time
    UNION ALL
    SELECT ''PAYMENT_UNRESOLVED'', p.id, p.order_id FROM dbo.tc_payments p JOIN dbo.tc_orders o ON o.id = p.order_id
    WHERE o.event_id = @event_id AND p.status IN (''PENDING'', ''UNKNOWN'')
    UNION ALL
    SELECT ''REFUND_REQUEST_OPEN'', rr.id, rr.order_id FROM dbo.tc_refund_requests rr JOIN dbo.tc_orders o ON o.id = rr.order_id
    WHERE o.event_id = @event_id AND rr.status IN (''PENDING'', ''APPROVED'')
      AND NOT (rr.status = ''APPROVED'' AND EXISTS (SELECT 1 FROM dbo.tc_refunds r WHERE r.refund_request_id = rr.id AND r.status = ''SUCCEEDED''))
    UNION ALL
    SELECT ''REFUND_UNRESOLVED'', r.id, p.order_id FROM dbo.tc_refunds r JOIN dbo.tc_payments p ON p.id = r.payment_id
    JOIN dbo.tc_orders o ON o.id = p.order_id WHERE o.event_id = @event_id AND r.status IN (''PENDING'', ''UNKNOWN'')
    UNION ALL
    SELECT ''CANCELLATION_PENDING'', x.id, x.aggregate_id FROM dbo.tc_outbox x
    WHERE x.aggregate_type = ''EVENT'' AND x.aggregate_id = @event_id
      AND x.event_type = ''EVENT_CANCELLED'' AND x.status <> ''PUBLISHED''
);
');

EXEC(N'
CREATE OR ALTER FUNCTION dbo.fn_GetOrganizationCashFlow(
    @organization_id uniqueidentifier, @from_utc datetime2(3), @to_utc datetime2(3)
)
RETURNS TABLE
AS RETURN (
    WITH flows AS (
        SELECT CASE WHEN EXISTS (SELECT 1 FROM dbo.tc_refunds r WHERE r.payment_id = p.id AND r.purpose = ''PAYMENT_COMPENSATION'')
                    THEN ''COMPENSATION_CAPTURE'' ELSE ''TICKET_CAPTURE'' END flow_type,
               p.amount inflow, CAST(0 AS decimal(19,0)) outflow
        FROM dbo.tc_payments p JOIN dbo.tc_orders o ON o.id = p.order_id
        JOIN dbo.tc_events e ON e.id = o.event_id
        WHERE e.organization_id = @organization_id AND p.status = ''CAPTURED''
          AND p.captured_at >= @from_utc AND p.captured_at < @to_utc
        UNION ALL
        SELECT CASE WHEN r.purpose = ''PAYMENT_COMPENSATION'' THEN ''COMPENSATION_REFUND'' ELSE ''TICKET_REFUND'' END,
               CAST(0 AS decimal(19,0)), r.amount
        FROM dbo.tc_refunds r JOIN dbo.tc_payments p ON p.id = r.payment_id
        JOIN dbo.tc_orders o ON o.id = p.order_id JOIN dbo.tc_events e ON e.id = o.event_id
        WHERE e.organization_id = @organization_id AND r.status = ''SUCCEEDED''
          AND r.processed_at >= @from_utc AND r.processed_at < @to_utc
    )
    SELECT flow_type, SUM(inflow) inflow_amount, SUM(outflow) outflow_amount,
           SUM(inflow - outflow) net_amount FROM flows GROUP BY flow_type
);
');

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_seats') AND name = N'IX_Seat_Zone_Status')
    CREATE INDEX IX_Seat_Zone_Status ON dbo.tc_seats(zone_id, status, id) INCLUDE(row_name, seat_number);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_ticket_holds') AND name = N'IX_TicketHold_Status_ExpiresAt')
    CREATE INDEX IX_TicketHold_Status_ExpiresAt ON dbo.tc_ticket_holds(status, expires_at, id) INCLUDE(user_id, event_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_orders') AND name = N'IX_Order_Event_Status_PaidAt')
    CREATE INDEX IX_Order_Event_Status_PaidAt ON dbo.tc_orders(event_id, status, paid_at, id) INCLUDE(user_id, total_amount);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_tickets') AND name = N'IX_Ticket_OrderItem_Status')
    CREATE INDEX IX_Ticket_OrderItem_Status ON dbo.tc_tickets(order_item_id, status, id) INCLUDE(paid_amount);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_payouts') AND name = N'IX_Payout_Settlement_Status')
    CREATE INDEX IX_Payout_Settlement_Status ON dbo.tc_payouts(settlement_id, status, id) INCLUDE(amount);
