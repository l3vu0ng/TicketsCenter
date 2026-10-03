SET NOCOUNT ON;
SET XACT_ABORT ON;

-- IX02, IX06, IX07, IX08, IX11 and IX12 already exist in their owning
-- migrations. The guards make a fresh schema build assert the whole catalog.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_events') AND name = N'IX_Event_Status_StartTime')
    CREATE INDEX IX_Event_Status_StartTime ON dbo.tc_events(status, start_time, id)
    INCLUDE(category_id, organization_id, title, cover_image_url);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_seats') AND name = N'IX_Seat_Zone_Status')
    CREATE INDEX IX_Seat_Zone_Status ON dbo.tc_seats(zone_id, status, id) INCLUDE(row_name, seat_number);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_orders') AND name = N'IX_Order_User_CreatedAt')
    CREATE INDEX IX_Order_User_CreatedAt ON dbo.tc_orders(user_id, created_at DESC, id)
    INCLUDE(event_id, status, total_amount);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_payments') AND name = N'IX_Payment_Status_CreatedAt')
    CREATE INDEX IX_Payment_Status_CreatedAt ON dbo.tc_payments(status, created_at, id) INCLUDE(order_id, txn_ref);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_check_ins') AND name = N'IX_CheckIn_Event_ScannedAt')
    CREATE INDEX IX_CheckIn_Event_ScannedAt ON dbo.tc_check_ins(event_id, scanned_at DESC, id) INCLUDE(result, ticket_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_ticket_holds') AND name = N'IX_TicketHold_Status_ExpiresAt')
    CREATE INDEX IX_TicketHold_Status_ExpiresAt ON dbo.tc_ticket_holds(status, expires_at, id) INCLUDE(user_id, event_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_orders') AND name = N'IX_Order_Event_Status_PaidAt')
    CREATE INDEX IX_Order_Event_Status_PaidAt ON dbo.tc_orders(event_id, status, paid_at, id) INCLUDE(user_id, total_amount);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_refunds') AND name = N'IX_Refund_Status_CreatedAt')
    CREATE INDEX IX_Refund_Status_CreatedAt ON dbo.tc_refunds(status, created_at, id)
    INCLUDE(payment_id, refund_request_id, amount, purpose);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_coupon_redemptions') AND name = N'IX_CouponRedemption_Coupon_Status')
    CREATE INDEX IX_CouponRedemption_Coupon_Status ON dbo.tc_coupon_redemptions(coupon_id, status, order_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_refund_requests') AND name = N'IX_RefundRequest_Status_RequestedAt')
    CREATE INDEX IX_RefundRequest_Status_RequestedAt ON dbo.tc_refund_requests(status, requested_at, id)
    INCLUDE(order_id, reason_type);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_tickets') AND name = N'IX_Ticket_OrderItem_Status')
    CREATE INDEX IX_Ticket_OrderItem_Status ON dbo.tc_tickets(order_item_id, status, id) INCLUDE(paid_amount);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_payouts') AND name = N'IX_Payout_Settlement_Status')
    CREATE INDEX IX_Payout_Settlement_Status ON dbo.tc_payouts(settlement_id, status, id) INCLUDE(amount);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_audit_logs') AND name = N'IX_AuditLog_Aggregate_CreatedAt')
    CREATE INDEX IX_AuditLog_Aggregate_CreatedAt ON dbo.tc_audit_logs(aggregate_type, aggregate_id, created_at DESC, id)
    INCLUDE(action);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_commission_rules') AND name = N'IX_CommissionRule_Organization_EffectiveFrom')
    CREATE INDEX IX_CommissionRule_Organization_EffectiveFrom ON dbo.tc_commission_rules(organization_id, effective_from, id)
    INCLUDE(effective_to, rate_percent, fixed_fee);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.tc_events') AND name = N'IX_Event_Organization_Status_StartTime')
    CREATE INDEX IX_Event_Organization_Status_StartTime ON dbo.tc_events(organization_id, status, start_time, id)
    INCLUDE(title, category_id);
