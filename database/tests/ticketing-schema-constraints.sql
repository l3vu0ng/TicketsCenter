SET NOCOUNT ON;
SET XACT_ABORT OFF;

-- Behavioral contract for the ticketing schema; all fixture writes roll back.

BEGIN TRANSACTION;
BEGIN TRY
    EXEC(N'
        CREATE PROCEDURE #expect_constraint
            @case_name nvarchar(200),
            @sql nvarchar(max),
            @expected nvarchar(128)
        AS
        BEGIN
            DECLARE @failed bit = 0;
            BEGIN TRY
                EXEC sys.sp_executesql @sql;
            END TRY
            BEGIN CATCH
                IF @expected IS NOT NULL AND ERROR_MESSAGE() NOT LIKE N''%'' + @expected + N''%''
                    THROW;
                SET @failed = 1;
            END CATCH;
            IF @failed = 0
                THROW 51000, @case_name, 1;
        END
    ');

    INSERT dbo.tc_users(id, email, normalized_email, password_hash, full_name, email_verified_at)
    VALUES
        ('02000000-0000-0000-0000-000000000001', N'buyer.one@example.test', N'buyer.one@example.test', N'test-hash-one', N'Người mua Một', SYSUTCDATETIME()),
        ('02000000-0000-0000-0000-000000000002', N'buyer.two@example.test', N'buyer.two@example.test', N'test-hash-two', N'Người mua Hai', SYSUTCDATETIME());

    INSERT dbo.tc_organizations(id, name, contact_email)
    VALUES ('02000000-0000-0000-0000-000000000010', N'Tổ chức Thử nghiệm', N'org@example.test');

    INSERT dbo.tc_organization_memberships(id, user_id, organization_id, role)
    VALUES ('02000000-0000-0000-0000-000000000011', '02000000-0000-0000-0000-000000000001', '02000000-0000-0000-0000-000000000010', 'MANAGER');

    INSERT dbo.tc_organization_requests(id, applicant_id, name, contact_email)
    VALUES ('02000000-0000-0000-0000-000000000012', '02000000-0000-0000-0000-000000000002', N'Tổ chức Mặc định', N'default@example.test');

    IF (SELECT status FROM dbo.tc_organization_requests WHERE id = '02000000-0000-0000-0000-000000000012') <> 'PENDING'
        THROW 51000, N'C10: default status must be PENDING', 1;

    INSERT dbo.tc_event_categories(id, name, slug)
    VALUES ('02000000-0000-0000-0000-000000000020', N'Âm nhạc', 'music-test');

    INSERT dbo.tc_commission_rules(id, organization_id, rate_percent, fixed_fee, effective_from, effective_to)
    VALUES ('02000000-0000-0000-0000-000000000021', '02000000-0000-0000-0000-000000000010', 5.5, 1000, '2026-01-01', '2027-01-01');

    INSERT dbo.tc_events(
        id, organization_id, category_id, commission_rule_id, title, venue_name, venue_address,
        sale_start, sale_end, start_time, end_time, status
    ) VALUES (
        '02000000-0000-0000-0000-000000000022', '02000000-0000-0000-0000-000000000010',
        '02000000-0000-0000-0000-000000000020', '02000000-0000-0000-0000-000000000021',
        N'Đêm nhạc Việt', N'Nhà hát', N'Hà Nội', '2026-10-01', '2026-10-10', '2026-10-11', '2026-10-12', 'PUBLISHED'
    );

    INSERT dbo.tc_events(
        id, organization_id, category_id, commission_rule_id, title, venue_name, venue_address,
        sale_start, sale_end, start_time, end_time, status
    ) VALUES (
        '02000000-0000-0000-0000-000000000043', '02000000-0000-0000-0000-000000000010',
        '02000000-0000-0000-0000-000000000020', '02000000-0000-0000-0000-000000000021',
        N'Sự kiện phụ', N'Nhà hát', N'Hà Nội', '2026-11-01', '2026-11-10', '2026-11-11', '2026-11-12', 'DRAFT'
    );

    INSERT dbo.tc_zones(id, event_id, name, type, price, capacity)
    VALUES
        ('02000000-0000-0000-0000-000000000023', '02000000-0000-0000-0000-000000000022', N'Ghế miễn phí', 'SEATED', 0, NULL),
        ('02000000-0000-0000-0000-000000000024', '02000000-0000-0000-0000-000000000022', N'Đứng', 'STANDING', 200000, 3);

    INSERT dbo.tc_seats(id, zone_id, row_name, seat_number, label)
    VALUES ('02000000-0000-0000-0000-000000000025', '02000000-0000-0000-0000-000000000023', N'A', 1, N'A1');

    INSERT dbo.tc_ticket_holds(id, user_id, event_id, created_at, expires_at)
    VALUES ('02000000-0000-0000-0000-000000000030', '02000000-0000-0000-0000-000000000001', '02000000-0000-0000-0000-000000000022', '2026-10-01T00:00:00', '2026-10-01T00:10:00');

    INSERT dbo.tc_ticket_holds(id, user_id, event_id, status, created_at, expires_at)
    VALUES
        ('02000000-0000-0000-0000-000000000044', '02000000-0000-0000-0000-000000000002', '02000000-0000-0000-0000-000000000043', 'RELEASED', '2026-11-01T00:00:00', '2026-11-01T00:10:00'),
        ('02000000-0000-0000-0000-000000000046', '02000000-0000-0000-0000-000000000002', '02000000-0000-0000-0000-000000000022', 'RELEASED', '2026-10-02T00:00:00', '2026-10-02T00:10:00');

    INSERT dbo.tc_ticket_hold_items(id, hold_id, zone_id, seat_id, quantity, unit_price)
    VALUES ('02000000-0000-0000-0000-000000000031', '02000000-0000-0000-0000-000000000030', '02000000-0000-0000-0000-000000000023', '02000000-0000-0000-0000-000000000025', 1, 0);

    INSERT dbo.tc_coupons(id, organization_id, code, discount_type, percentage_value, max_uses, valid_from, valid_to)
    VALUES ('02000000-0000-0000-0000-000000000032', '02000000-0000-0000-0000-000000000010', 'PERCENT30', 'PERCENTAGE', 30, 10, '2026-01-01', '2027-01-01');

    INSERT dbo.tc_orders(id, user_id, event_id, hold_id, coupon_id, order_code, subtotal_amount, discount_amount, total_amount)
    VALUES ('02000000-0000-0000-0000-000000000033', '02000000-0000-0000-0000-000000000001', '02000000-0000-0000-0000-000000000022', '02000000-0000-0000-0000-000000000030', '02000000-0000-0000-0000-000000000032', 'ORDER-SCHEMA-VALID', 100, 30, 70);

    INSERT dbo.tc_orders(id, user_id, event_id, hold_id, order_code, subtotal_amount, discount_amount, total_amount)
    VALUES ('02000000-0000-0000-0000-000000000045', '02000000-0000-0000-0000-000000000002', '02000000-0000-0000-0000-000000000043', '02000000-0000-0000-0000-000000000044', 'ORDER-SCHEMA-SECOND', 0, 0, 0);

    INSERT dbo.tc_order_items(id, order_id, zone_id, seat_id, quantity, unit_price, zone_name_snapshot, seat_label_snapshot)
    VALUES ('02000000-0000-0000-0000-000000000034', '02000000-0000-0000-0000-000000000033', '02000000-0000-0000-0000-000000000023', '02000000-0000-0000-0000-000000000025', 1, 0, N'Ghế miễn phí', N'A1');

    INSERT dbo.tc_payments(id, order_id, txn_ref, amount)
    VALUES ('02000000-0000-0000-0000-000000000035', '02000000-0000-0000-0000-000000000033', 'TXN-SCHEMA-VALID', 70);

    INSERT dbo.tc_coupon_redemptions(id, coupon_id, order_id, expires_at)
    VALUES ('02000000-0000-0000-0000-000000000036', '02000000-0000-0000-0000-000000000032', '02000000-0000-0000-0000-000000000033', DATEADD(minute, 10, SYSUTCDATETIME()));

    INSERT dbo.tc_tickets(id, order_item_id, ticket_code, qr_secret_hash, paid_amount)
    VALUES
        ('02000000-0000-0000-0000-000000000037', '02000000-0000-0000-0000-000000000034', 'TICKET-SCHEMA-VALID', 0x01, 70),
        ('02000000-0000-0000-0000-000000000048', '02000000-0000-0000-0000-000000000034', 'TICKET-SCHEMA-FREE', 0x02, 0);

    INSERT dbo.tc_refund_requests(id, order_id, requester_id, reason, reason_type)
    VALUES ('02000000-0000-0000-0000-000000000038', '02000000-0000-0000-0000-000000000033', '02000000-0000-0000-0000-000000000001', N'Không thể tham dự', 'CUSTOMER_REQUEST');

    INSERT dbo.tc_refund_requests(id, order_id, requester_id, reason, reason_type)
    VALUES ('02000000-0000-0000-0000-000000000047', '02000000-0000-0000-0000-000000000033', '02000000-0000-0000-0000-000000000001', N'Yêu cầu thứ hai', 'CUSTOMER_REQUEST');

    INSERT dbo.tc_refund_request_tickets(refund_request_id, ticket_id)
    VALUES ('02000000-0000-0000-0000-000000000038', '02000000-0000-0000-0000-000000000037');

    INSERT dbo.tc_refunds(id, refund_request_id, payment_id, purpose, amount)
    VALUES ('02000000-0000-0000-0000-000000000039', '02000000-0000-0000-0000-000000000038', '02000000-0000-0000-0000-000000000035', 'CUSTOMER_REFUND', 70);

    INSERT dbo.tc_settlements(id, event_id, gross_revenue, total_refund, total_commission, net_payable)
    VALUES ('02000000-0000-0000-0000-000000000040', '02000000-0000-0000-0000-000000000022', 70, 10, 10, 50);

    INSERT dbo.tc_settlement_items(id, settlement_id, order_id, gross_amount, refund_amount, commission_amount, net_amount)
    VALUES ('02000000-0000-0000-0000-000000000041', '02000000-0000-0000-0000-000000000040', '02000000-0000-0000-0000-000000000033', 70, 10, 10, 50);

    INSERT dbo.tc_payouts(id, settlement_id, amount, reference)
    VALUES ('02000000-0000-0000-0000-000000000042', '02000000-0000-0000-0000-000000000040', 50, 'PAYOUT-SCHEMA-VALID');

    EXEC #expect_constraint N'C01 duplicate normalized email escaped',
        N'INSERT dbo.tc_users(id,email,normalized_email,password_hash,full_name) VALUES(''02000000-0000-0000-0000-000000000101'',N'' Buyer.One@Example.Test '',N''buyer.one@example.test'',N''x'',N''Duplicate'')',
        N'UQ_User_NormalizedEmail';
    EXEC #expect_constraint N'C01 NULL normalized email escaped',
        N'INSERT dbo.tc_users(id,email,normalized_email,password_hash,full_name) VALUES(''02000000-0000-0000-0000-000000000102'',N''null@example.test'',NULL,N''x'',N''Null'')',
        N'normalized_email';
    EXEC #expect_constraint N'C02 duplicate membership escaped',
        N'INSERT dbo.tc_organization_memberships(id,user_id,organization_id,role) VALUES(''02000000-0000-0000-0000-000000000103'',''02000000-0000-0000-0000-000000000001'',''02000000-0000-0000-0000-000000000010'',''CHECK_IN_STAFF'')',
        N'UQ_Membership_User_Organization';
    EXEC #expect_constraint N'C03 duplicate seat escaped',
        N'INSERT dbo.tc_seats(id,zone_id,row_name,seat_number,label) VALUES(''02000000-0000-0000-0000-000000000104'',''02000000-0000-0000-0000-000000000023'',N''A'',1,N''A01-duplicate'')',
        N'UQ_Seat_Zone_Row_Number';
    EXEC #expect_constraint N'C04 invalid event time escaped',
        N'INSERT dbo.tc_events(id,organization_id,category_id,title,venue_name,venue_address,sale_start,sale_end,start_time,end_time) VALUES(''02000000-0000-0000-0000-000000000105'',''02000000-0000-0000-0000-000000000010'',''02000000-0000-0000-0000-000000000020'',N''Bad'',N''V'',N''A'',''2026-10-01'',''2026-10-01'',''2026-10-02'',''2026-10-03'')',
        N'CK_Event_TimeRange';
    EXEC #expect_constraint N'C05 negative zone price escaped',
        N'INSERT dbo.tc_zones(id,event_id,name,type,price) VALUES(''02000000-0000-0000-0000-000000000106'',''02000000-0000-0000-0000-000000000022'',N''Bad price'',''SEATED'',-1)',
        N'CK_Zone_Price_Quota';
    EXEC #expect_constraint N'C06 mixed coupon type escaped',
        N'INSERT dbo.tc_coupons(id,organization_id,code,discount_type,percentage_value,fixed_amount,max_uses,valid_from,valid_to) VALUES(''02000000-0000-0000-0000-000000000107'',''02000000-0000-0000-0000-000000000010'',''BAD-BOTH'',''PERCENTAGE'',10,1000,1,''2026-01-01'',''2027-01-01'')',
        N'CK_Coupon_Discount_Limit';
    EXEC #expect_constraint N'C06 NULL percentage value escaped',
        N'INSERT dbo.tc_coupons(id,organization_id,code,discount_type,max_uses,valid_from,valid_to) VALUES(''02000000-0000-0000-0000-000000000120'',''02000000-0000-0000-0000-000000000010'',''BAD-NULL'',''PERCENTAGE'',1,''2026-01-01'',''2027-01-01'')',
        N'CK_Coupon_Discount_Limit';
    EXEC #expect_constraint N'C06 NULL fixed amount escaped',
        N'INSERT dbo.tc_coupons(id,organization_id,code,discount_type,max_uses,valid_from,valid_to) VALUES(''02000000-0000-0000-0000-000000000121'',''02000000-0000-0000-0000-000000000010'',''BAD-FIXED-NULL'',''FIXED_AMOUNT'',1,''2026-01-01'',''2027-01-01'')',
        N'CK_Coupon_Discount_Limit';
    EXEC #expect_constraint N'C07 seated hold quantity escaped',
        N'INSERT dbo.tc_ticket_hold_items(id,hold_id,zone_id,seat_id,quantity,unit_price) VALUES(''02000000-0000-0000-0000-000000000108'',''02000000-0000-0000-0000-000000000044'',''02000000-0000-0000-0000-000000000023'',''02000000-0000-0000-0000-000000000025'',2,0)',
        N'CK_HoldItem_Seat_Quantity';
    EXEC #expect_constraint N'C07 seated order quantity escaped',
        N'INSERT dbo.tc_order_items(id,order_id,zone_id,seat_id,quantity,unit_price,zone_name_snapshot,seat_label_snapshot) VALUES(''02000000-0000-0000-0000-000000000109'',''02000000-0000-0000-0000-000000000045'',''02000000-0000-0000-0000-000000000023'',''02000000-0000-0000-0000-000000000025'',2,0,N''Z'',N''A1'')',
        N'CK_OrderItem_Seat_Quantity';
    EXEC #expect_constraint N'C08 inconsistent order amount escaped',
        N'INSERT dbo.tc_orders(id,user_id,event_id,hold_id,order_code,subtotal_amount,discount_amount,total_amount) VALUES(''02000000-0000-0000-0000-000000000110'',''02000000-0000-0000-0000-000000000002'',''02000000-0000-0000-0000-000000000022'',''02000000-0000-0000-0000-000000000046'',''ORDER-SCHEMA-BAD'',100,31,69)',
        N'CK_Order_Amounts';
    EXEC #expect_constraint N'C09 duplicate txnRef escaped',
        N'INSERT dbo.tc_payments(id,order_id,txn_ref,amount) VALUES(''02000000-0000-0000-0000-000000000111'',''02000000-0000-0000-0000-000000000033'',''TXN-SCHEMA-VALID'',70)',
        N'UQ_Payment_TxnRef';
    EXEC #expect_constraint N'C10 invalid request status escaped',
        N'INSERT dbo.tc_organization_requests(id,applicant_id,name,contact_email,status) VALUES(''02000000-0000-0000-0000-000000000112'',''02000000-0000-0000-0000-000000000002'',N''Bad'',N''bad@example.test'',''UNKNOWN'')',
        N'CK_OrganizationRequest_Status';
    EXEC #expect_constraint N'C11 invalid hold time escaped',
        N'INSERT dbo.tc_ticket_holds(id,user_id,event_id,created_at,expires_at) VALUES(''02000000-0000-0000-0000-000000000113'',''02000000-0000-0000-0000-000000000002'',''02000000-0000-0000-0000-000000000022'',''2026-10-01'',''2026-10-01'')',
        N'CK_TicketHold_TimeRange';
    EXEC #expect_constraint N'C12 negative hold price escaped',
        N'INSERT dbo.tc_ticket_hold_items(id,hold_id,zone_id,quantity,unit_price) VALUES(''02000000-0000-0000-0000-000000000114'',''02000000-0000-0000-0000-000000000030'',''02000000-0000-0000-0000-000000000024'',1,-1)',
        N'CK_HoldItem_UnitPrice';
    EXEC #expect_constraint N'C12 negative order price escaped',
        N'INSERT dbo.tc_order_items(id,order_id,zone_id,quantity,unit_price,zone_name_snapshot) VALUES(''02000000-0000-0000-0000-000000000115'',''02000000-0000-0000-0000-000000000045'',''02000000-0000-0000-0000-000000000024'',1,-1,N''Đứng'')',
        N'CK_OrderItem_UnitPrice';
    EXEC #expect_constraint N'C13 negative paid amount escaped',
        N'INSERT dbo.tc_tickets(id,order_item_id,ticket_code,qr_secret_hash,paid_amount) VALUES(''02000000-0000-0000-0000-000000000116'',''02000000-0000-0000-0000-000000000034'',''TICKET-SCHEMA-BAD'',0x01,-1)',
        N'CK_Ticket_PaidAmount';
    EXEC #expect_constraint N'C14 zero payment escaped',
        N'INSERT dbo.tc_payments(id,order_id,txn_ref,amount) VALUES(''02000000-0000-0000-0000-000000000117'',''02000000-0000-0000-0000-000000000033'',''TXN-SCHEMA-ZERO'',0)',
        N'CK_Payment_PositiveAmount';
    EXEC #expect_constraint N'C15 zero refund escaped',
        N'INSERT dbo.tc_refunds(id,refund_request_id,payment_id,purpose,amount) VALUES(''02000000-0000-0000-0000-000000000118'',''02000000-0000-0000-0000-000000000038'',''02000000-0000-0000-0000-000000000035'',''CUSTOMER_REFUND'',0)',
        N'CK_Refund_PositiveAmount';
    EXEC #expect_constraint N'C16 invalid commission terms escaped',
        N'INSERT dbo.tc_commission_rules(id,organization_id,rate_percent,fixed_fee,effective_from,effective_to) VALUES(''02000000-0000-0000-0000-000000000119'',''02000000-0000-0000-0000-000000000010'',-1,0,''2026-01-01'',''2026-01-01'')',
        N'CK_CommissionRule_Terms';
    EXEC #expect_constraint N'C17 inconsistent settlement item escaped',
        N'INSERT dbo.tc_settlement_items(id,settlement_id,order_id,gross_amount,refund_amount,commission_amount,net_amount) VALUES(''02000000-0000-0000-0000-000000000120'',''02000000-0000-0000-0000-000000000040'',''02000000-0000-0000-0000-000000000045'',70,10,10,49)',
        N'CK_SettlementItem_Amounts';
    EXEC #expect_constraint N'C18 inconsistent settlement escaped',
        N'INSERT dbo.tc_settlements(id,event_id,gross_revenue,total_refund,total_commission,net_payable) VALUES(''02000000-0000-0000-0000-000000000121'',''02000000-0000-0000-0000-000000000043'',70,10,10,49)',
        N'CK_Settlement_Amounts';
    EXEC #expect_constraint N'C19 zero payout escaped',
        N'INSERT dbo.tc_payouts(id,settlement_id,amount,reference) VALUES(''02000000-0000-0000-0000-000000000122'',''02000000-0000-0000-0000-000000000040'',0,''PAYOUT-SCHEMA-ZERO'')',
        N'CK_Payout_PositiveAmount';
    EXEC #expect_constraint N'C20 compensation with request escaped',
        N'INSERT dbo.tc_refunds(id,refund_request_id,payment_id,purpose,amount) VALUES(''02000000-0000-0000-0000-000000000123'',''02000000-0000-0000-0000-000000000038'',''02000000-0000-0000-0000-000000000035'',''PAYMENT_COMPENSATION'',1)',
        N'CK_Refund_Purpose_Request';
    EXEC #expect_constraint N'wrong enum escaped',
        N'INSERT dbo.tc_users(id,email,normalized_email,password_hash,full_name,status) VALUES(''02000000-0000-0000-0000-000000000124'',N''enum@example.test'',N''enum@example.test'',N''x'',N''Enum'',''UNKNOWN'')',
        N'CK_Users_Status';
    EXEC #expect_constraint N'foreign key escaped',
        N'INSERT dbo.tc_organization_memberships(id,user_id,organization_id,role) VALUES(''02000000-0000-0000-0000-000000000125'',''02000000-0000-0000-0000-000000009999'',''02000000-0000-0000-0000-000000000010'',''MANAGER'')',
        N'FK_OrganizationMemberships_User';
    EXEC #expect_constraint N'one active hold per user escaped',
        N'INSERT dbo.tc_ticket_holds(id,user_id,event_id,created_at,expires_at) VALUES(''02000000-0000-0000-0000-000000000126'',''02000000-0000-0000-0000-000000000001'',''02000000-0000-0000-0000-000000000022'',''2026-10-02'',''2026-10-02T00:10:00'')',
        N'UX_TicketHolds_OneActivePerUser';
    EXEC #expect_constraint N'one open request per ticket escaped',
        N'INSERT dbo.tc_refund_request_tickets(refund_request_id,ticket_id) VALUES(''02000000-0000-0000-0000-000000000047'',''02000000-0000-0000-0000-000000000037'')',
        N'UX_RefundRequestTickets_OneOpenPerTicket';

    DROP PROCEDURE #expect_constraint;
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
