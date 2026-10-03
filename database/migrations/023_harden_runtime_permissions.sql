SET NOCOUNT ON;
SET XACT_ABORT ON;

-- Check-in needs the ticket/check-in module only.  Explicit DENY also wins
-- over a future broad/public grant and keeps financial objects out of this pool.
DENY SELECT ON dbo.vw_TicketDetails TO tc_checkin;
DENY SELECT ON dbo.tc_orders TO tc_checkin;
DENY SELECT ON dbo.tc_order_items TO tc_checkin;
DENY SELECT ON dbo.tc_payments TO tc_checkin;
DENY SELECT ON dbo.tc_refund_requests TO tc_checkin;
DENY SELECT ON dbo.tc_refunds TO tc_checkin;
DENY SELECT ON dbo.tc_settlements TO tc_checkin;
DENY SELECT ON dbo.tc_settlement_items TO tc_checkin;
DENY SELECT ON dbo.tc_payouts TO tc_checkin;
DENY EXECUTE ON OBJECT::dbo.fn_CalculateCommission TO tc_checkin;
DENY SELECT ON OBJECT::dbo.fn_GetSettlementBlockers TO tc_checkin;
