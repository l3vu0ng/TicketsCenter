SET NOCOUNT ON;
SET XACT_ABORT OFF;
BEGIN TRANSACTION;
BEGIN TRY
    DECLARE @from datetime2(3)='2026-09-01T00:00:00', @to datetime2(3)='2026-10-01T00:00:00',
            @admin uniqueidentifier=NEWID(), @buyer uniqueidentifier=NEWID(), @org uniqueidentifier=NEWID(),
            @category uniqueidentifier=NEWID(), @rule uniqueidentifier=NEWID(),
            @event uniqueidentifier=NEWID(), @emptyEvent uniqueidentifier=NEWID(), @fullEvent uniqueidentifier=NEWID(),
            @zone uniqueidentifier=NEWID(), @emptyZone uniqueidentifier=NEWID(), @fullZone uniqueidentifier=NEWID(),
            @hold uniqueidentifier=NEWID(), @hold2 uniqueidentifier=NEWID(), @fullHold uniqueidentifier=NEWID(),
            @outsideOrder uniqueidentifier=NEWID(), @insideOrder uniqueidentifier=NEWID(), @fullOrder uniqueidentifier=NEWID(),
            @outsideItem uniqueidentifier=NEWID(), @insideItem uniqueidentifier=NEWID(), @fullItem uniqueidentifier=NEWID(),
            @outsidePayment uniqueidentifier=NEWID(), @insidePayment uniqueidentifier=NEWID(), @failedPayment uniqueidentifier=NEWID(),
            @fullPayment uniqueidentifier=NEWID(), @outsideTicket uniqueidentifier=NEWID(), @insideTicket uniqueidentifier=NEWID(),
            @fullTicket uniqueidentifier=NEWID(), @outsideRequest uniqueidentifier=NEWID(), @fullRequest uniqueidentifier=NEWID(),
            @outsideRefund uniqueidentifier=NEWID(), @failedRefund uniqueidentifier=NEWID(), @fullRefund uniqueidentifier=NEWID();

    INSERT dbo.tc_users(id,email,normalized_email,password_hash,full_name,email_verified_at) VALUES
        (@admin,CONCAT('d18-admin-',@admin,'@example.test'),CONCAT('d18-admin-',@admin,'@example.test'),'test','D18 Admin',@from),
        (@buyer,CONCAT('d18-buyer-',@buyer,'@example.test'),CONCAT('d18-buyer-',@buyer,'@example.test'),'test','D18 Buyer',@from);
    INSERT dbo.tc_user_platform_roles(user_id,role) VALUES(@admin,'ADMIN');
    INSERT dbo.tc_organizations(id,name,contact_email) VALUES(@org,N'Tổ chức Ngày 18','day18@example.test');
    INSERT dbo.tc_event_categories(id,name,slug) VALUES(@category,N'Báo cáo',CONCAT('day-18-',@category));
    INSERT dbo.tc_commission_rules(id,organization_id,rate_percent,fixed_fee,effective_from,effective_to)
    VALUES(@rule,@org,10,0,'2026-01-01','2027-01-01');
    INSERT dbo.tc_events(id,organization_id,category_id,commission_rule_id,title,venue_name,venue_address,
                         sale_start,sale_end,start_time,end_time,status) VALUES
        (@event,@org,@category,@rule,N'=HYPERLINK("https://example.test")','Venue','Address','2026-07-01','2026-08-01','2026-10-02','2026-10-03','DRAFT'),
        (@emptyEvent,@org,@category,@rule,N'Sự kiện rỗng','Venue','Address','2026-07-01','2026-08-01','2026-10-02','2026-10-03','DRAFT'),
        (@fullEvent,@org,@category,@rule,N'Hoàn toàn bộ','Venue','Address','2026-07-01','2026-08-01','2026-10-02','2026-10-03','DRAFT');
    INSERT dbo.tc_zones(id,event_id,name,type,price,capacity) VALUES
        (@zone,@event,'Standing','STANDING',100000,10),(@emptyZone,@emptyEvent,'Standing','STANDING',100000,1),
        (@fullZone,@fullEvent,'Standing','STANDING',100000,1);
    UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id IN (@event,@emptyEvent,@fullEvent);
    INSERT dbo.tc_ticket_holds(id,user_id,event_id,status,created_at,expires_at) VALUES
        (@hold,@buyer,@event,'CONSUMED','2026-07-31','2026-08-01'),
        (@hold2,@buyer,@event,'CONSUMED','2026-09-10','2026-09-11'),
        (@fullHold,@buyer,@fullEvent,'CONSUMED','2026-09-10','2026-09-11');
    INSERT dbo.tc_orders(id,user_id,event_id,hold_id,order_code,subtotal_amount,discount_amount,total_amount,status,paid_at) VALUES
        (@outsideOrder,@buyer,@event,@hold,CONCAT('D18-',@outsideOrder),100000,0,100000,'PAID','2026-08-15'),
        (@insideOrder,@buyer,@event,@hold2,CONCAT('D18-',@insideOrder),200000,0,200000,'PAID','2026-09-10'),
        (@fullOrder,@buyer,@fullEvent,@fullHold,CONCAT('D18-',@fullOrder),100000,0,100000,'PAID','2026-09-10');
    INSERT dbo.tc_order_items(id,order_id,zone_id,quantity,unit_price,zone_name_snapshot) VALUES
        (@outsideItem,@outsideOrder,@zone,1,100000,'Standing'),(@insideItem,@insideOrder,@zone,1,200000,'Standing'),
        (@fullItem,@fullOrder,@fullZone,1,100000,'Standing');
    INSERT dbo.tc_payments(id,order_id,txn_ref,amount,status,captured_at) VALUES
        (@outsidePayment,@outsideOrder,CONCAT('D18-',@outsidePayment),100000,'CAPTURED','2026-08-15'),
        (@insidePayment,@insideOrder,CONCAT('D18-',@insidePayment),200000,'CAPTURED','2026-09-10'),
        (@failedPayment,@insideOrder,CONCAT('D18-',@failedPayment),200000,'FAILED',NULL),
        (@fullPayment,@fullOrder,CONCAT('D18-',@fullPayment),100000,'CAPTURED','2026-09-10');
    INSERT dbo.tc_tickets(id,order_item_id,ticket_code,qr_secret_hash,paid_amount,status) VALUES
        (@outsideTicket,@outsideItem,CONCAT('D18-',@outsideTicket),0x01,100000,'REFUNDED'),
        (@insideTicket,@insideItem,CONCAT('D18-',@insideTicket),0x02,200000,'ACTIVE'),
        (@fullTicket,@fullItem,CONCAT('D18-',@fullTicket),0x03,100000,'REFUNDED');
    INSERT dbo.tc_refund_requests(id,order_id,requester_id,reviewer_id,reason,reason_type,status,decided_at) VALUES
        (@outsideRequest,@outsideOrder,@buyer,@admin,'Outside cohort','CUSTOMER_REQUEST','COMPLETED','2026-09-20'),
        (@fullRequest,@fullOrder,@buyer,@admin,'Full refund','CUSTOMER_REQUEST','COMPLETED','2026-09-20');
    INSERT dbo.tc_refund_request_tickets(refund_request_id,ticket_id,is_open) VALUES
        (@outsideRequest,@outsideTicket,0),(@fullRequest,@fullTicket,0);
    INSERT dbo.tc_refunds(id,refund_request_id,payment_id,purpose,amount,status,provider_reference,processed_at) VALUES
        (@outsideRefund,@outsideRequest,@outsidePayment,'CUSTOMER_REFUND',100000,'SUCCEEDED','D18-OUT','2026-09-20'),
        (@failedRefund,@outsideRequest,@outsidePayment,'CUSTOMER_REFUND',100000,'FAILED','D18-FAIL','2026-09-19'),
        (@fullRefund,@fullRequest,@fullPayment,'CUSTOMER_REFUND',100000,'SUCCEEDED','D18-FULL','2026-09-20');

    IF NOT EXISTS (SELECT 1 FROM dbo.fn_GetOrganizationRevenue(@org,@from,@to)
                   WHERE paid_order_count=2 AND gross_revenue=300000 AND refunded_amount=100000 AND net_revenue=200000)
        THROW 52100, 'F05 cohort or historical cutoff is wrong', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.fn_GetOrganizationCashFlow(@org,@from,@to)
                   WHERE flow_type='TICKET_REFUND' AND outflow_amount=200000)
        THROW 52101, 'F10 excluded an in-period refund for an outside-period order', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.fn_GetOrganizationCashFlow(@org,@from,@to)
                   WHERE flow_type='TICKET_CAPTURE' AND inflow_amount=300000)
        THROW 52102, 'F10 capture total or failed-attempt handling is wrong', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.vw_EventSalesReport WHERE event_id=@emptyEvent AND paid_order_count=0
                   AND gross_revenue=0 AND total_refund=0 AND total_commission=0 AND net_payable=0)
        THROW 52103, 'V04 omitted or misstated an event with no orders', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.vw_EventSalesReport WHERE event_id=@fullEvent
                   AND gross_revenue=100000 AND total_refund=100000 AND total_commission=0 AND net_payable=0)
        THROW 52104, 'V04 full refund commission is not zero', 1;
    IF NOT EXISTS(SELECT 1 FROM dbo.vw_EventSalesReport WHERE event_id=@event
                  AND gross_revenue=300000 AND total_refund=100000 AND total_commission=20000 AND net_payable=180000)
        THROW 52105, 'V04 multiplied money across payment or refund attempts', 1;

    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;

DECLARE @updateBlocked bit=0;
BEGIN TRANSACTION;
BEGIN TRY
    INSERT dbo.tc_audit_logs(action,aggregate_type,aggregate_id) VALUES
        ('D18_A','TEST',NEWID()),('D18_B','TEST',NEWID());
    UPDATE dbo.tc_audit_logs SET action='TAMPERED' WHERE action IN ('D18_A','D18_B');
END TRY
BEGIN CATCH
    IF ERROR_NUMBER()=51110 SET @updateBlocked=1; ELSE BEGIN
        IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
        THROW;
    END;
END CATCH;
IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
IF @updateBlocked=0 THROW 52106, 'TR10 allowed multirow UPDATE', 1;

DECLARE @deleteBlocked bit=0;
BEGIN TRANSACTION;
BEGIN TRY
    INSERT dbo.tc_audit_logs(action,aggregate_type,aggregate_id) VALUES
        ('D18_C','TEST',NEWID()),('D18_D','TEST',NEWID());
    DELETE dbo.tc_audit_logs WHERE action IN ('D18_C','D18_D');
END TRY
BEGIN CATCH
    IF ERROR_NUMBER()=51110 SET @deleteBlocked=1; ELSE BEGIN
        IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
        THROW;
    END;
END CATCH;
IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
IF @deleteBlocked=0 THROW 52107, 'TR10 allowed multirow DELETE', 1;

DECLARE @rollbackId bigint;
BEGIN TRANSACTION;
INSERT dbo.tc_audit_logs(action,aggregate_type,aggregate_id) VALUES('D18_ROLLBACK','TEST',NEWID());
SET @rollbackId=SCOPE_IDENTITY();
ROLLBACK TRANSACTION;
IF EXISTS(SELECT 1 FROM dbo.tc_audit_logs WHERE id=@rollbackId)
    THROW 52108, 'TR10 prevented normal rollback of an inserted audit row', 1;
