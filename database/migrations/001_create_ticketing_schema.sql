SET NOCOUNT ON;
SET XACT_ABORT ON;

IF OBJECT_ID(N'dbo.tc_users', N'U') IS NULL
BEGIN

CREATE TABLE dbo.tc_users (
    id uniqueidentifier NOT NULL CONSTRAINT DF_Users_Id DEFAULT NEWSEQUENTIALID(),
    email nvarchar(320) NOT NULL,
    normalized_email nvarchar(320) NOT NULL,
    password_hash nvarchar(255) NOT NULL,
    full_name nvarchar(200) NOT NULL,
    phone nvarchar(30) NULL,
    status varchar(20) NOT NULL CONSTRAINT DF_Users_Status DEFAULT 'ACTIVE',
    email_verified_at datetime2(3) NULL,
    auth_version int NOT NULL CONSTRAINT DF_Users_AuthVersion DEFAULT 0,
    version bigint NOT NULL CONSTRAINT DF_Users_Version DEFAULT 0,
    created_at datetime2(3) NOT NULL CONSTRAINT DF_Users_CreatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_Users PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_user_platform_roles (
    user_id uniqueidentifier NOT NULL,
    role varchar(30) NOT NULL,
    granted_at datetime2(3) NOT NULL CONSTRAINT DF_UserPlatformRoles_GrantedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_UserPlatformRoles PRIMARY KEY (user_id, role)
);

CREATE TABLE dbo.tc_organizations (
    id uniqueidentifier NOT NULL CONSTRAINT DF_Organizations_Id DEFAULT NEWSEQUENTIALID(),
    name nvarchar(200) NOT NULL,
    contact_email nvarchar(320) NOT NULL,
    contact_phone nvarchar(30) NULL,
    description nvarchar(2000) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT DF_Organizations_CreatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_Organizations PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_organization_memberships (
    id uniqueidentifier NOT NULL CONSTRAINT DF_OrganizationMemberships_Id DEFAULT NEWSEQUENTIALID(),
    user_id uniqueidentifier NOT NULL,
    organization_id uniqueidentifier NOT NULL,
    role varchar(30) NOT NULL,
    active bit NOT NULL CONSTRAINT DF_OrganizationMemberships_Active DEFAULT 1,
    joined_at datetime2(3) NOT NULL CONSTRAINT DF_OrganizationMemberships_JoinedAt DEFAULT SYSUTCDATETIME(),
    version bigint NOT NULL CONSTRAINT DF_OrganizationMemberships_Version DEFAULT 0,
    CONSTRAINT PK_OrganizationMemberships PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_organization_requests (
    id uniqueidentifier NOT NULL CONSTRAINT DF_OrganizationRequests_Id DEFAULT NEWSEQUENTIALID(),
    applicant_id uniqueidentifier NOT NULL,
    reviewer_id uniqueidentifier NULL,
    organization_id uniqueidentifier NULL,
    name nvarchar(200) NOT NULL,
    contact_email nvarchar(320) NOT NULL,
    contact_phone nvarchar(30) NULL,
    description nvarchar(2000) NULL,
    status varchar(30) NOT NULL CONSTRAINT DF_OrganizationRequest_Status DEFAULT 'PENDING',
    requested_at datetime2(3) NOT NULL CONSTRAINT DF_OrganizationRequests_RequestedAt DEFAULT SYSUTCDATETIME(),
    decided_at datetime2(3) NULL,
    rejection_reason nvarchar(1000) NULL,
    version bigint NOT NULL CONSTRAINT DF_OrganizationRequests_Version DEFAULT 0,
    CONSTRAINT PK_OrganizationRequests PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_event_categories (
    id uniqueidentifier NOT NULL CONSTRAINT DF_EventCategories_Id DEFAULT NEWSEQUENTIALID(),
    name nvarchar(120) NOT NULL,
    slug varchar(120) NOT NULL,
    CONSTRAINT PK_EventCategories PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_commission_rules (
    id uniqueidentifier NOT NULL CONSTRAINT DF_CommissionRules_Id DEFAULT NEWSEQUENTIALID(),
    organization_id uniqueidentifier NOT NULL,
    rate_percent decimal(7,4) NOT NULL,
    fixed_fee decimal(19,0) NOT NULL,
    effective_from datetime2(3) NOT NULL,
    effective_to datetime2(3) NOT NULL,
    CONSTRAINT PK_CommissionRules PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_events (
    id uniqueidentifier NOT NULL CONSTRAINT DF_Events_Id DEFAULT NEWSEQUENTIALID(),
    organization_id uniqueidentifier NOT NULL,
    category_id uniqueidentifier NOT NULL,
    commission_rule_id uniqueidentifier NULL,
    title nvarchar(250) NOT NULL,
    description nvarchar(max) NULL,
    cover_image_url nvarchar(2048) NULL,
    venue_name nvarchar(250) NOT NULL,
    venue_address nvarchar(500) NOT NULL,
    sale_start datetime2(3) NOT NULL,
    sale_end datetime2(3) NOT NULL,
    start_time datetime2(3) NOT NULL,
    end_time datetime2(3) NOT NULL,
    status varchar(30) NOT NULL CONSTRAINT DF_Events_Status DEFAULT 'DRAFT',
    version bigint NOT NULL CONSTRAINT DF_Events_Version DEFAULT 0,
    created_at datetime2(3) NOT NULL CONSTRAINT DF_Events_CreatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_Events PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_zones (
    id uniqueidentifier NOT NULL CONSTRAINT DF_Zones_Id DEFAULT NEWSEQUENTIALID(),
    event_id uniqueidentifier NOT NULL,
    name nvarchar(120) NOT NULL,
    type varchar(20) NOT NULL,
    price decimal(19,0) NOT NULL,
    capacity int NULL,
    held_quantity int NOT NULL CONSTRAINT DF_Zones_HeldQuantity DEFAULT 0,
    sold_quantity int NOT NULL CONSTRAINT DF_Zones_SoldQuantity DEFAULT 0,
    version bigint NOT NULL CONSTRAINT DF_Zones_Version DEFAULT 0,
    CONSTRAINT PK_Zones PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_seats (
    id uniqueidentifier NOT NULL CONSTRAINT DF_Seats_Id DEFAULT NEWSEQUENTIALID(),
    zone_id uniqueidentifier NOT NULL,
    row_name nvarchar(20) NOT NULL,
    seat_number int NOT NULL,
    label nvarchar(50) NOT NULL,
    status varchar(20) NOT NULL CONSTRAINT DF_Seats_Status DEFAULT 'AVAILABLE',
    version bigint NOT NULL CONSTRAINT DF_Seats_Version DEFAULT 0,
    CONSTRAINT PK_Seats PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_ticket_holds (
    id uniqueidentifier NOT NULL CONSTRAINT DF_TicketHolds_Id DEFAULT NEWSEQUENTIALID(),
    user_id uniqueidentifier NOT NULL,
    event_id uniqueidentifier NOT NULL,
    status varchar(20) NOT NULL CONSTRAINT DF_TicketHolds_Status DEFAULT 'ACTIVE',
    created_at datetime2(3) NOT NULL CONSTRAINT DF_TicketHolds_CreatedAt DEFAULT SYSUTCDATETIME(),
    expires_at datetime2(3) NOT NULL,
    version bigint NOT NULL CONSTRAINT DF_TicketHolds_Version DEFAULT 0,
    CONSTRAINT PK_TicketHolds PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_ticket_hold_items (
    id uniqueidentifier NOT NULL CONSTRAINT DF_TicketHoldItems_Id DEFAULT NEWSEQUENTIALID(),
    hold_id uniqueidentifier NOT NULL,
    zone_id uniqueidentifier NOT NULL,
    seat_id uniqueidentifier NULL,
    quantity int NOT NULL,
    unit_price decimal(19,0) NOT NULL,
    CONSTRAINT PK_TicketHoldItems PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_coupons (
    id uniqueidentifier NOT NULL CONSTRAINT DF_Coupons_Id DEFAULT NEWSEQUENTIALID(),
    organization_id uniqueidentifier NOT NULL,
    code varchar(80) NOT NULL,
    discount_type varchar(30) NOT NULL,
    percentage_value decimal(5,2) NULL,
    fixed_amount decimal(19,0) NULL,
    max_discount_amount decimal(19,0) NULL,
    max_uses int NOT NULL,
    valid_from datetime2(3) NOT NULL,
    valid_to datetime2(3) NOT NULL,
    active bit NOT NULL CONSTRAINT DF_Coupons_Active DEFAULT 1,
    version bigint NOT NULL CONSTRAINT DF_Coupons_Version DEFAULT 0,
    CONSTRAINT PK_Coupons PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_orders (
    id uniqueidentifier NOT NULL CONSTRAINT DF_Orders_Id DEFAULT NEWSEQUENTIALID(),
    user_id uniqueidentifier NOT NULL,
    event_id uniqueidentifier NOT NULL,
    hold_id uniqueidentifier NOT NULL,
    coupon_id uniqueidentifier NULL,
    order_code varchar(64) NOT NULL,
    subtotal_amount decimal(19,0) NOT NULL,
    discount_amount decimal(19,0) NOT NULL,
    total_amount decimal(19,0) NOT NULL,
    status varchar(30) NOT NULL CONSTRAINT DF_Orders_Status DEFAULT 'PENDING_PAYMENT',
    created_at datetime2(3) NOT NULL CONSTRAINT DF_Orders_CreatedAt DEFAULT SYSUTCDATETIME(),
    paid_at datetime2(3) NULL,
    version bigint NOT NULL CONSTRAINT DF_Orders_Version DEFAULT 0,
    CONSTRAINT PK_Orders PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_order_items (
    id uniqueidentifier NOT NULL CONSTRAINT DF_OrderItems_Id DEFAULT NEWSEQUENTIALID(),
    order_id uniqueidentifier NOT NULL,
    zone_id uniqueidentifier NOT NULL,
    seat_id uniqueidentifier NULL,
    quantity int NOT NULL,
    unit_price decimal(19,0) NOT NULL,
    zone_name_snapshot nvarchar(120) NOT NULL,
    seat_label_snapshot nvarchar(50) NULL,
    CONSTRAINT PK_OrderItems PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_payments (
    id uniqueidentifier NOT NULL CONSTRAINT DF_Payments_Id DEFAULT NEWSEQUENTIALID(),
    order_id uniqueidentifier NOT NULL,
    txn_ref varchar(100) NOT NULL,
    amount decimal(19,0) NOT NULL,
    currency char(3) NOT NULL CONSTRAINT DF_Payments_Currency DEFAULT 'VND',
    status varchar(20) NOT NULL CONSTRAINT DF_Payments_Status DEFAULT 'PENDING',
    provider_reference nvarchar(200) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT DF_Payments_CreatedAt DEFAULT SYSUTCDATETIME(),
    captured_at datetime2(3) NULL,
    version bigint NOT NULL CONSTRAINT DF_Payments_Version DEFAULT 0,
    CONSTRAINT PK_Payments PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_coupon_redemptions (
    id uniqueidentifier NOT NULL CONSTRAINT DF_CouponRedemptions_Id DEFAULT NEWSEQUENTIALID(),
    coupon_id uniqueidentifier NOT NULL,
    order_id uniqueidentifier NOT NULL,
    status varchar(20) NOT NULL CONSTRAINT DF_CouponRedemptions_Status DEFAULT 'RESERVED',
    reserved_at datetime2(3) NOT NULL CONSTRAINT DF_CouponRedemptions_ReservedAt DEFAULT SYSUTCDATETIME(),
    expires_at datetime2(3) NOT NULL,
    consumed_at datetime2(3) NULL,
    released_at datetime2(3) NULL,
    CONSTRAINT PK_CouponRedemptions PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_tickets (
    id uniqueidentifier NOT NULL CONSTRAINT DF_Tickets_Id DEFAULT NEWSEQUENTIALID(),
    order_item_id uniqueidentifier NOT NULL,
    ticket_code varchar(100) NOT NULL,
    qr_secret_hash varbinary(64) NOT NULL,
    paid_amount decimal(19,0) NOT NULL,
    status varchar(30) NOT NULL CONSTRAINT DF_Tickets_Status DEFAULT 'ACTIVE',
    issued_at datetime2(3) NOT NULL CONSTRAINT DF_Tickets_IssuedAt DEFAULT SYSUTCDATETIME(),
    version bigint NOT NULL CONSTRAINT DF_Tickets_Version DEFAULT 0,
    CONSTRAINT PK_Tickets PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_check_ins (
    id uniqueidentifier NOT NULL CONSTRAINT DF_CheckIns_Id DEFAULT NEWSEQUENTIALID(),
    event_id uniqueidentifier NOT NULL,
    ticket_id uniqueidentifier NULL,
    actor_id uniqueidentifier NOT NULL,
    result varchar(30) NOT NULL,
    scanned_at datetime2(3) NOT NULL CONSTRAINT DF_CheckIns_ScannedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_CheckIns PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_refund_requests (
    id uniqueidentifier NOT NULL CONSTRAINT DF_RefundRequests_Id DEFAULT NEWSEQUENTIALID(),
    order_id uniqueidentifier NOT NULL,
    requester_id uniqueidentifier NOT NULL,
    reviewer_id uniqueidentifier NULL,
    reason nvarchar(2000) NOT NULL,
    reason_type varchar(30) NOT NULL,
    status varchar(20) NOT NULL CONSTRAINT DF_RefundRequests_Status DEFAULT 'PENDING',
    requested_at datetime2(3) NOT NULL CONSTRAINT DF_RefundRequests_RequestedAt DEFAULT SYSUTCDATETIME(),
    decided_at datetime2(3) NULL,
    rejection_reason nvarchar(1000) NULL,
    version bigint NOT NULL CONSTRAINT DF_RefundRequests_Version DEFAULT 0,
    CONSTRAINT PK_RefundRequests PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_refund_request_tickets (
    refund_request_id uniqueidentifier NOT NULL,
    ticket_id uniqueidentifier NOT NULL,
    is_open bit NOT NULL CONSTRAINT DF_RefundRequestTickets_IsOpen DEFAULT 1,
    added_at datetime2(3) NOT NULL CONSTRAINT DF_RefundRequestTickets_AddedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_RefundRequestTickets PRIMARY KEY (refund_request_id, ticket_id)
);

CREATE TABLE dbo.tc_refunds (
    id uniqueidentifier NOT NULL CONSTRAINT DF_Refunds_Id DEFAULT NEWSEQUENTIALID(),
    refund_request_id uniqueidentifier NULL,
    payment_id uniqueidentifier NOT NULL,
    purpose varchar(30) NOT NULL,
    amount decimal(19,0) NOT NULL,
    status varchar(20) NOT NULL CONSTRAINT DF_Refunds_Status DEFAULT 'PENDING',
    provider_reference nvarchar(200) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT DF_Refunds_CreatedAt DEFAULT SYSUTCDATETIME(),
    processed_at datetime2(3) NULL,
    version bigint NOT NULL CONSTRAINT DF_Refunds_Version DEFAULT 0,
    CONSTRAINT PK_Refunds PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_settlements (
    id uniqueidentifier NOT NULL CONSTRAINT DF_Settlements_Id DEFAULT NEWSEQUENTIALID(),
    event_id uniqueidentifier NOT NULL,
    gross_revenue decimal(19,0) NOT NULL CONSTRAINT DF_Settlements_GrossRevenue DEFAULT 0,
    total_refund decimal(19,0) NOT NULL CONSTRAINT DF_Settlements_TotalRefund DEFAULT 0,
    total_commission decimal(19,0) NOT NULL CONSTRAINT DF_Settlements_TotalCommission DEFAULT 0,
    net_payable decimal(19,0) NOT NULL CONSTRAINT DF_Settlements_NetPayable DEFAULT 0,
    status varchar(20) NOT NULL CONSTRAINT DF_Settlements_Status DEFAULT 'DRAFT',
    confirmed_at datetime2(3) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT DF_Settlements_CreatedAt DEFAULT SYSUTCDATETIME(),
    version bigint NOT NULL CONSTRAINT DF_Settlements_Version DEFAULT 0,
    CONSTRAINT PK_Settlements PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_settlement_items (
    id uniqueidentifier NOT NULL CONSTRAINT DF_SettlementItems_Id DEFAULT NEWSEQUENTIALID(),
    settlement_id uniqueidentifier NOT NULL,
    order_id uniqueidentifier NOT NULL,
    gross_amount decimal(19,0) NOT NULL,
    refund_amount decimal(19,0) NOT NULL,
    commission_amount decimal(19,0) NOT NULL,
    net_amount decimal(19,0) NOT NULL,
    CONSTRAINT PK_SettlementItems PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_payouts (
    id uniqueidentifier NOT NULL CONSTRAINT DF_Payouts_Id DEFAULT NEWSEQUENTIALID(),
    settlement_id uniqueidentifier NOT NULL,
    amount decimal(19,0) NOT NULL,
    reference varchar(100) NOT NULL,
    status varchar(20) NOT NULL CONSTRAINT DF_Payouts_Status DEFAULT 'PENDING',
    created_at datetime2(3) NOT NULL CONSTRAINT DF_Payouts_CreatedAt DEFAULT SYSUTCDATETIME(),
    paid_at datetime2(3) NULL,
    version bigint NOT NULL CONSTRAINT DF_Payouts_Version DEFAULT 0,
    CONSTRAINT PK_Payouts PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_audit_logs (
    id bigint IDENTITY(1,1) NOT NULL,
    actor_id uniqueidentifier NULL,
    action varchar(100) NOT NULL,
    aggregate_type varchar(100) NOT NULL,
    aggregate_id uniqueidentifier NOT NULL,
    detail nvarchar(max) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT DF_AuditLogs_CreatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_AuditLogs PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_otps (
    id uniqueidentifier NOT NULL CONSTRAINT DF_Otps_Id DEFAULT NEWSEQUENTIALID(),
    user_id uniqueidentifier NULL,
    email_normalized nvarchar(320) NOT NULL,
    purpose varchar(30) NOT NULL,
    secret_hash varbinary(64) NOT NULL,
    expires_at datetime2(3) NOT NULL,
    failed_attempts tinyint NOT NULL CONSTRAINT DF_Otps_FailedAttempts DEFAULT 0,
    consumed_at datetime2(3) NULL,
    invalidated_at datetime2(3) NULL,
    created_at datetime2(3) NOT NULL CONSTRAINT DF_Otps_CreatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_Otps PRIMARY KEY (id)
);

CREATE TABLE dbo.tc_outbox (
    id uniqueidentifier NOT NULL CONSTRAINT DF_Outbox_Id DEFAULT NEWSEQUENTIALID(),
    event_type varchar(120) NOT NULL,
    aggregate_type varchar(100) NOT NULL,
    aggregate_id uniqueidentifier NOT NULL,
    payload nvarchar(max) NOT NULL,
    idempotency_key varchar(200) NOT NULL,
    status varchar(20) NOT NULL CONSTRAINT DF_Outbox_Status DEFAULT 'PENDING',
    available_at datetime2(3) NOT NULL CONSTRAINT DF_Outbox_AvailableAt DEFAULT SYSUTCDATETIME(),
    lease_owner varchar(120) NULL,
    lease_until datetime2(3) NULL,
    attempts int NOT NULL CONSTRAINT DF_Outbox_Attempts DEFAULT 0,
    created_at datetime2(3) NOT NULL CONSTRAINT DF_Outbox_CreatedAt DEFAULT SYSUTCDATETIME(),
    published_at datetime2(3) NULL,
    CONSTRAINT PK_Outbox PRIMARY KEY (id)
);
END;
