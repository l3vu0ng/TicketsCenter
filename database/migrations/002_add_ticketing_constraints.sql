SET NOCOUNT ON;
SET XACT_ABORT ON;

IF OBJECT_ID(N'dbo.CK_Event_TimeRange', N'C') IS NULL
BEGIN

ALTER TABLE dbo.tc_user_platform_roles ADD
    CONSTRAINT FK_UserPlatformRoles_User FOREIGN KEY (user_id) REFERENCES dbo.tc_users(id),
    CONSTRAINT CK_UserPlatformRoles_Role CHECK (role IN ('ADMIN'));

ALTER TABLE dbo.tc_organization_memberships ADD
    CONSTRAINT FK_OrganizationMemberships_User FOREIGN KEY (user_id) REFERENCES dbo.tc_users(id),
    CONSTRAINT FK_OrganizationMemberships_Organization FOREIGN KEY (organization_id) REFERENCES dbo.tc_organizations(id),
    CONSTRAINT CK_OrganizationMemberships_Role CHECK (role IN ('MANAGER', 'CHECK_IN_STAFF')),
    CONSTRAINT UQ_Membership_User_Organization UNIQUE (user_id, organization_id);

ALTER TABLE dbo.tc_organization_requests ADD
    CONSTRAINT FK_OrganizationRequests_Applicant FOREIGN KEY (applicant_id) REFERENCES dbo.tc_users(id),
    CONSTRAINT FK_OrganizationRequests_Reviewer FOREIGN KEY (reviewer_id) REFERENCES dbo.tc_users(id),
    CONSTRAINT FK_OrganizationRequests_Organization FOREIGN KEY (organization_id) REFERENCES dbo.tc_organizations(id),
    CONSTRAINT CK_OrganizationRequest_Status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'));

ALTER TABLE dbo.tc_commission_rules ADD
    CONSTRAINT FK_CommissionRules_Organization FOREIGN KEY (organization_id) REFERENCES dbo.tc_organizations(id),
    CONSTRAINT CK_CommissionRule_Terms CHECK (
        rate_percent >= 0 AND fixed_fee >= 0 AND effective_from < effective_to
    );

ALTER TABLE dbo.tc_events ADD
    CONSTRAINT FK_Events_Organization FOREIGN KEY (organization_id) REFERENCES dbo.tc_organizations(id),
    CONSTRAINT FK_Events_Category FOREIGN KEY (category_id) REFERENCES dbo.tc_event_categories(id),
    CONSTRAINT FK_Events_CommissionRule FOREIGN KEY (commission_rule_id) REFERENCES dbo.tc_commission_rules(id),
    CONSTRAINT CK_Events_Status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'REJECTED', 'PUBLISHED', 'CANCELLED')),
    CONSTRAINT CK_Event_TimeRange CHECK (
        sale_start < sale_end AND sale_end <= start_time AND start_time < end_time
    );

ALTER TABLE dbo.tc_zones ADD
    CONSTRAINT FK_Zones_Event FOREIGN KEY (event_id) REFERENCES dbo.tc_events(id),
    CONSTRAINT CK_Zones_Type CHECK (type IN ('SEATED', 'STANDING')),
    CONSTRAINT CK_Zone_Price_Quota CHECK (
        price >= 0 AND held_quantity >= 0 AND sold_quantity >= 0 AND
        ((type = 'STANDING' AND capacity IS NOT NULL AND capacity > 0 AND held_quantity + sold_quantity <= capacity)
         OR (type = 'SEATED' AND capacity IS NULL AND held_quantity = 0 AND sold_quantity = 0))
    ),
    CONSTRAINT UQ_Zones_Event_Name UNIQUE (event_id, name);

ALTER TABLE dbo.tc_seats ADD
    CONSTRAINT FK_Seats_Zone FOREIGN KEY (zone_id) REFERENCES dbo.tc_zones(id),
    CONSTRAINT CK_Seats_Number CHECK (seat_number > 0),
    CONSTRAINT CK_Seats_Status CHECK (status IN ('AVAILABLE', 'HELD', 'SOLD')),
    CONSTRAINT UQ_Seat_Zone_Row_Number UNIQUE (zone_id, row_name, seat_number),
    CONSTRAINT UQ_Seats_Zone_Label UNIQUE (zone_id, label);

ALTER TABLE dbo.tc_ticket_holds ADD
    CONSTRAINT FK_TicketHolds_User FOREIGN KEY (user_id) REFERENCES dbo.tc_users(id),
    CONSTRAINT FK_TicketHolds_Event FOREIGN KEY (event_id) REFERENCES dbo.tc_events(id),
    CONSTRAINT CK_TicketHolds_Status CHECK (status IN ('ACTIVE', 'RELEASED', 'CONSUMED')),
    CONSTRAINT CK_TicketHold_TimeRange CHECK (expires_at > created_at);

ALTER TABLE dbo.tc_ticket_hold_items ADD
    CONSTRAINT FK_TicketHoldItems_Hold FOREIGN KEY (hold_id) REFERENCES dbo.tc_ticket_holds(id),
    CONSTRAINT FK_TicketHoldItems_Zone FOREIGN KEY (zone_id) REFERENCES dbo.tc_zones(id),
    CONSTRAINT FK_TicketHoldItems_Seat FOREIGN KEY (seat_id) REFERENCES dbo.tc_seats(id),
    CONSTRAINT CK_HoldItem_Seat_Quantity CHECK (
        (seat_id IS NOT NULL AND quantity = 1) OR (seat_id IS NULL AND quantity > 0)
    ),
    CONSTRAINT CK_HoldItem_UnitPrice CHECK (unit_price >= 0);

ALTER TABLE dbo.tc_coupons ADD
    CONSTRAINT FK_Coupons_Organization FOREIGN KEY (organization_id) REFERENCES dbo.tc_organizations(id),
    CONSTRAINT CK_Coupons_DiscountType CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    CONSTRAINT CK_Coupon_Discount_Limit CHECK (
        max_uses > 0 AND valid_from < valid_to AND
        (max_discount_amount IS NULL OR max_discount_amount >= 0) AND
        ((discount_type = 'PERCENTAGE' AND percentage_value > 0 AND percentage_value <= 30 AND fixed_amount IS NULL)
         OR (discount_type = 'FIXED_AMOUNT' AND fixed_amount > 0 AND percentage_value IS NULL))
    ),
    CONSTRAINT UQ_Coupons_Organization_Code UNIQUE (organization_id, code);

ALTER TABLE dbo.tc_orders ADD
    CONSTRAINT FK_Orders_User FOREIGN KEY (user_id) REFERENCES dbo.tc_users(id),
    CONSTRAINT FK_Orders_Event FOREIGN KEY (event_id) REFERENCES dbo.tc_events(id),
    CONSTRAINT FK_Orders_Hold FOREIGN KEY (hold_id) REFERENCES dbo.tc_ticket_holds(id),
    CONSTRAINT FK_Orders_Coupon FOREIGN KEY (coupon_id) REFERENCES dbo.tc_coupons(id),
    CONSTRAINT CK_Orders_Status CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT CK_Order_Amounts CHECK (
        subtotal_amount >= 0 AND discount_amount >= 0 AND total_amount >= 0 AND
        total_amount = subtotal_amount - discount_amount AND
        discount_amount * 10 <= subtotal_amount * 3
    ),
    CONSTRAINT UQ_Orders_OrderCode UNIQUE (order_code),
    CONSTRAINT UQ_Orders_Hold UNIQUE (hold_id);

ALTER TABLE dbo.tc_order_items ADD
    CONSTRAINT FK_OrderItems_Order FOREIGN KEY (order_id) REFERENCES dbo.tc_orders(id),
    CONSTRAINT FK_OrderItems_Zone FOREIGN KEY (zone_id) REFERENCES dbo.tc_zones(id),
    CONSTRAINT FK_OrderItems_Seat FOREIGN KEY (seat_id) REFERENCES dbo.tc_seats(id),
    CONSTRAINT CK_OrderItem_Seat_Quantity CHECK (
        (seat_id IS NOT NULL AND quantity = 1) OR (seat_id IS NULL AND quantity > 0)
    ),
    CONSTRAINT CK_OrderItem_UnitPrice CHECK (unit_price >= 0);

ALTER TABLE dbo.tc_payments ADD
    CONSTRAINT FK_Payments_Order FOREIGN KEY (order_id) REFERENCES dbo.tc_orders(id),
    CONSTRAINT CK_Payments_Currency CHECK (currency = 'VND'),
    CONSTRAINT CK_Payments_Status CHECK (status IN ('PENDING', 'CAPTURED', 'FAILED', 'UNKNOWN')),
    CONSTRAINT CK_Payment_PositiveAmount CHECK (amount > 0),
    CONSTRAINT UQ_Payment_TxnRef UNIQUE (txn_ref);

ALTER TABLE dbo.tc_coupon_redemptions ADD
    CONSTRAINT FK_CouponRedemptions_Coupon FOREIGN KEY (coupon_id) REFERENCES dbo.tc_coupons(id),
    CONSTRAINT FK_CouponRedemptions_Order FOREIGN KEY (order_id) REFERENCES dbo.tc_orders(id),
    CONSTRAINT CK_CouponRedemptions_Status CHECK (status IN ('RESERVED', 'CONSUMED', 'RELEASED')),
    CONSTRAINT CK_CouponRedemptions_Time CHECK (expires_at > reserved_at),
    CONSTRAINT UQ_CouponRedemptions_Order UNIQUE (order_id);

ALTER TABLE dbo.tc_tickets ADD
    CONSTRAINT FK_Tickets_OrderItem FOREIGN KEY (order_item_id) REFERENCES dbo.tc_order_items(id),
    CONSTRAINT CK_Tickets_Status CHECK (status IN ('ACTIVE', 'USED', 'REFUND_PENDING', 'REFUNDED', 'INVALIDATED')),
    CONSTRAINT CK_Ticket_PaidAmount CHECK (paid_amount >= 0),
    CONSTRAINT UQ_Tickets_TicketCode UNIQUE (ticket_code);

ALTER TABLE dbo.tc_check_ins ADD
    CONSTRAINT FK_CheckIns_Event FOREIGN KEY (event_id) REFERENCES dbo.tc_events(id),
    CONSTRAINT FK_CheckIns_Ticket FOREIGN KEY (ticket_id) REFERENCES dbo.tc_tickets(id),
    CONSTRAINT FK_CheckIns_Actor FOREIGN KEY (actor_id) REFERENCES dbo.tc_users(id),
    CONSTRAINT CK_CheckIns_Result CHECK (result IN (
        'SUCCESS', 'NOT_FOUND', 'WRONG_EVENT', 'TOO_EARLY', 'TOO_LATE',
        'ALREADY_USED', 'NOT_ACTIVE', 'EVENT_CANCELLED'
    ));

ALTER TABLE dbo.tc_refund_requests ADD
    CONSTRAINT FK_RefundRequests_Order FOREIGN KEY (order_id) REFERENCES dbo.tc_orders(id),
    CONSTRAINT FK_RefundRequests_Requester FOREIGN KEY (requester_id) REFERENCES dbo.tc_users(id),
    CONSTRAINT FK_RefundRequests_Reviewer FOREIGN KEY (reviewer_id) REFERENCES dbo.tc_users(id),
    CONSTRAINT CK_RefundRequests_ReasonType CHECK (reason_type IN ('CUSTOMER_REQUEST', 'EVENT_CANCELLATION')),
    CONSTRAINT CK_RefundRequests_Status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'COMPLETED'));

ALTER TABLE dbo.tc_refund_request_tickets ADD
    CONSTRAINT FK_RefundRequestTickets_Request FOREIGN KEY (refund_request_id) REFERENCES dbo.tc_refund_requests(id),
    CONSTRAINT FK_RefundRequestTickets_Ticket FOREIGN KEY (ticket_id) REFERENCES dbo.tc_tickets(id);

ALTER TABLE dbo.tc_refunds ADD
    CONSTRAINT FK_Refunds_Request FOREIGN KEY (refund_request_id) REFERENCES dbo.tc_refund_requests(id),
    CONSTRAINT FK_Refunds_Payment FOREIGN KEY (payment_id) REFERENCES dbo.tc_payments(id),
    CONSTRAINT CK_Refunds_Status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'UNKNOWN')),
    CONSTRAINT CK_Refund_PositiveAmount CHECK (amount > 0),
    CONSTRAINT CK_Refund_Purpose_Request CHECK (
        (purpose = 'CUSTOMER_REFUND' AND refund_request_id IS NOT NULL)
        OR (purpose = 'PAYMENT_COMPENSATION' AND refund_request_id IS NULL)
    );

ALTER TABLE dbo.tc_settlements ADD
    CONSTRAINT FK_Settlements_Event FOREIGN KEY (event_id) REFERENCES dbo.tc_events(id),
    CONSTRAINT CK_Settlements_Status CHECK (status IN ('DRAFT', 'CONFIRMED', 'PAID')),
    CONSTRAINT CK_Settlement_Amounts CHECK (
        gross_revenue >= 0 AND total_refund >= 0 AND total_commission >= 0 AND net_payable >= 0 AND
        total_refund <= gross_revenue AND total_commission <= gross_revenue - total_refund AND
        net_payable = gross_revenue - total_refund - total_commission
    ),
    CONSTRAINT UQ_Settlements_Event UNIQUE (event_id);

ALTER TABLE dbo.tc_settlement_items ADD
    CONSTRAINT FK_SettlementItems_Settlement FOREIGN KEY (settlement_id) REFERENCES dbo.tc_settlements(id),
    CONSTRAINT FK_SettlementItems_Order FOREIGN KEY (order_id) REFERENCES dbo.tc_orders(id),
    CONSTRAINT CK_SettlementItem_Amounts CHECK (
        gross_amount >= 0 AND refund_amount >= 0 AND commission_amount >= 0 AND net_amount >= 0 AND
        refund_amount <= gross_amount AND commission_amount <= gross_amount - refund_amount AND
        net_amount = gross_amount - refund_amount - commission_amount
    ),
    CONSTRAINT UQ_SettlementItems_Order UNIQUE (order_id);

ALTER TABLE dbo.tc_payouts ADD
    CONSTRAINT FK_Payouts_Settlement FOREIGN KEY (settlement_id) REFERENCES dbo.tc_settlements(id),
    CONSTRAINT CK_Payouts_Status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT CK_Payout_PositiveAmount CHECK (amount > 0),
    CONSTRAINT UQ_Payouts_Reference UNIQUE (reference);

ALTER TABLE dbo.tc_audit_logs ADD
    CONSTRAINT FK_AuditLogs_Actor FOREIGN KEY (actor_id) REFERENCES dbo.tc_users(id);

ALTER TABLE dbo.tc_otps ADD
    CONSTRAINT FK_Otps_User FOREIGN KEY (user_id) REFERENCES dbo.tc_users(id),
    CONSTRAINT CK_Otps_Purpose CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    CONSTRAINT CK_Otps_Attempts CHECK (failed_attempts BETWEEN 0 AND 5),
    CONSTRAINT CK_Otps_Time CHECK (expires_at > created_at);

ALTER TABLE dbo.tc_outbox ADD
    CONSTRAINT CK_Outbox_Status CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT CK_Outbox_Attempts CHECK (attempts >= 0),
    CONSTRAINT CK_Outbox_Lease CHECK (
        (lease_owner IS NULL AND lease_until IS NULL) OR (lease_owner IS NOT NULL AND lease_until IS NOT NULL)
    ),
    CONSTRAINT UQ_Outbox_IdempotencyKey UNIQUE (idempotency_key);

ALTER TABLE dbo.tc_users ADD
    CONSTRAINT CK_Users_Status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT CK_Users_AuthVersion CHECK (auth_version >= 0),
    CONSTRAINT UQ_User_NormalizedEmail UNIQUE (normalized_email);

ALTER TABLE dbo.tc_event_categories ADD
    CONSTRAINT UQ_EventCategories_Slug UNIQUE (slug);

CREATE UNIQUE INDEX UX_TicketHolds_OneActivePerUser
    ON dbo.tc_ticket_holds(user_id) WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX UX_TicketHoldItems_Seat
    ON dbo.tc_ticket_hold_items(hold_id, seat_id) WHERE seat_id IS NOT NULL;

CREATE UNIQUE INDEX UX_OrderItems_Seat
    ON dbo.tc_order_items(order_id, seat_id) WHERE seat_id IS NOT NULL;

CREATE UNIQUE INDEX UX_RefundRequestTickets_OneOpenPerTicket
    ON dbo.tc_refund_request_tickets(ticket_id) WHERE is_open = 1;

CREATE INDEX IX_Otps_UserPurposeCreated
    ON dbo.tc_otps(user_id, purpose, created_at DESC);

CREATE INDEX IX_CouponRedemptions_CouponStatus
    ON dbo.tc_coupon_redemptions(coupon_id, status);

CREATE INDEX IX_Outbox_Claim
    ON dbo.tc_outbox(status, available_at, lease_until);
END;
