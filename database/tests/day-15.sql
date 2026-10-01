SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
BEGIN TRY
    DECLARE @now datetime2(3)=SYSUTCDATETIME(), @admin uniqueidentifier=NEWID(), @buyer uniqueidentifier=NEWID(),
            @org uniqueidentifier=NEWID(), @category uniqueidentifier=NEWID(), @event uniqueidentifier=NEWID(),
            @zone uniqueidentifier=NEWID(), @hold uniqueidentifier=NEWID(), @order uniqueidentifier=NEWID(),
            @coupon uniqueidentifier=NEWID(), @redemption uniqueidentifier=NEWID(),
            @item uniqueidentifier=NEWID(), @payment uniqueidentifier=NEWID(), @ticket1 uniqueidentifier=NEWID(),
            @ticket2 uniqueidentifier=NEWID(), @request1 uniqueidentifier=NEWID(), @request2 uniqueidentifier=NEWID(),
            @refund1 uniqueidentifier=NEWID(), @refund2 uniqueidentifier=NEWID();

    INSERT dbo.tc_users(id,email,normalized_email,password_hash,full_name,email_verified_at) VALUES
      (@admin,CONCAT('day15-admin-',@admin,'@example.test'),CONCAT('day15-admin-',@admin,'@example.test'),'test','Admin',@now),
      (@buyer,CONCAT('day15-buyer-',@buyer,'@example.test'),CONCAT('day15-buyer-',@buyer,'@example.test'),'test','Buyer',@now);
    INSERT dbo.tc_user_platform_roles(user_id,role) VALUES(@admin,'ADMIN');
    INSERT dbo.tc_organizations(id,name,contact_email) VALUES(@org,'Day 15','day15@example.test');
    INSERT dbo.tc_event_categories(id,name,slug) VALUES(@category,'Day 15',CONCAT('day-15-',@category));
    INSERT dbo.tc_events(id,organization_id,category_id,title,venue_name,venue_address,sale_start,sale_end,start_time,end_time,status)
    VALUES(@event,@org,@category,'Refund','Venue','Address',DATEADD(day,-1,@now),DATEADD(day,1,@now),DATEADD(day,2,@now),DATEADD(day,3,@now),'DRAFT');
    INSERT dbo.tc_zones(id,event_id,name,type,price,capacity,sold_quantity) VALUES(@zone,@event,'Standing','STANDING',250000,10,2);
    UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id=@event;
    INSERT dbo.tc_ticket_holds(id,user_id,event_id,status,expires_at) VALUES(@hold,@buyer,@event,'CONSUMED',DATEADD(minute,10,@now));
    INSERT dbo.tc_coupons(id,organization_id,code,discount_type,fixed_amount,max_uses,valid_from,valid_to)
    VALUES(@coupon,@org,CONCAT('D15-',@coupon),'FIXED_AMOUNT',100000,10,DATEADD(day,-1,@now),DATEADD(day,1,@now));
    INSERT dbo.tc_orders(id,user_id,event_id,hold_id,coupon_id,order_code,subtotal_amount,discount_amount,total_amount,status,paid_at)
    VALUES(@order,@buyer,@event,@hold,@coupon,CONCAT('D15-',@order),500000,100000,400000,'PAID',@now);
    INSERT dbo.tc_coupon_redemptions(id,coupon_id,order_id,status,expires_at,consumed_at)
    VALUES(@redemption,@coupon,@order,'CONSUMED',DATEADD(minute,10,@now),@now);
    INSERT dbo.tc_order_items(id,order_id,zone_id,quantity,unit_price,zone_name_snapshot) VALUES(@item,@order,@zone,2,250000,'Standing');
    INSERT dbo.tc_payments(id,order_id,txn_ref,amount,status,captured_at) VALUES(@payment,@order,CONCAT('D15-',@payment),400000,'CAPTURED',@now);
    INSERT dbo.tc_tickets(id,order_item_id,ticket_code,qr_secret_hash,paid_amount,status) VALUES
      (@ticket1,@item,CONCAT('D15-',@ticket1),0x01,160000,'REFUND_PENDING'),
      (@ticket2,@item,CONCAT('D15-',@ticket2),0x02,240000,'REFUND_PENDING');
    INSERT dbo.tc_refund_requests(id,order_id,requester_id,reviewer_id,reason,reason_type,status,decided_at) VALUES
      (@request1,@order,@buyer,@admin,N'First','CUSTOMER_REQUEST','APPROVED',@now),
      (@request2,@order,@buyer,@admin,N'Second','CUSTOMER_REQUEST','APPROVED',@now);
    INSERT dbo.tc_refund_request_tickets(refund_request_id,ticket_id) VALUES(@request1,@ticket1),(@request2,@ticket2);
    INSERT dbo.tc_refunds(id,refund_request_id,payment_id,purpose,amount) VALUES
      (@refund1,@request1,@payment,'CUSTOMER_REFUND',160000),(@refund2,@request2,@payment,'CUSTOMER_REFUND',240000);

    EXEC dbo.usp_ApplyRefundResult @refund1,N'{"status":"SUCCEEDED","providerReference":"d15-1"}';
    EXEC dbo.usp_ApplyRefundResult @refund1,N'{"status":"SUCCEEDED","providerReference":"old-callback"}';
    IF (SELECT sold_quantity FROM dbo.tc_zones WHERE id=@zone)<>1 THROW 51901,'duplicate returned standing inventory twice',1;
    EXEC dbo.usp_ApplyRefundResult @refund2,N'{"status":"SUCCEEDED","providerReference":"d15-2"}';
    IF (SELECT SUM(amount) FROM dbo.tc_refunds WHERE payment_id=@payment AND status='SUCCEEDED')<>400000 THROW 51902,'partial refunds did not total payment',1;
    IF (SELECT sold_quantity FROM dbo.tc_zones WHERE id=@zone)<>0 THROW 51903,'inventory was not returned',1;
    IF EXISTS(SELECT 1 FROM dbo.tc_tickets WHERE id IN(@ticket1,@ticket2) AND status<>'REFUNDED') THROW 51904,'tickets were not refunded',1;
    IF (SELECT status FROM dbo.tc_coupon_redemptions WHERE id=@redemption)<>'CONSUMED' THROW 51912,'refund changed consumed coupon history',1;

    DECLARE @excess uniqueidentifier=NEWID();
    INSERT dbo.tc_refunds(id,payment_id,purpose,amount) VALUES(@excess,@payment,'PAYMENT_COMPENSATION',1);
    BEGIN TRY
      EXEC dbo.usp_ApplyRefundResult @excess,N'{"status":"SUCCEEDED","providerReference":"excess"}';
      THROW 51905,'excess refund succeeded',1;
    END TRY BEGIN CATCH IF ERROR_NUMBER()=51905 THROW; END CATCH;
    IF (SELECT status FROM dbo.tc_refunds WHERE id=@excess)<>'PENDING' THROW 51906,'failed TX11 did not rollback refund',1;

    DECLARE @failed uniqueidentifier=NEWID(), @unknown uniqueidentifier=NEWID();
    INSERT dbo.tc_refunds(id,payment_id,purpose,amount) VALUES(@failed,@payment,'PAYMENT_COMPENSATION',1),(@unknown,@payment,'PAYMENT_COMPENSATION',1);
    EXEC dbo.usp_ApplyRefundResult @failed,N'{"status":"FAILED","providerReference":"failed"}';
    EXEC dbo.usp_ApplyRefundResult @unknown,N'{"status":"UNKNOWN","providerReference":"unknown"}';
    IF (SELECT status FROM dbo.tc_refunds WHERE id=@failed)<>'FAILED' OR (SELECT status FROM dbo.tc_refunds WHERE id=@unknown)<>'UNKNOWN'
      THROW 51907,'non-success result was not retained',1;

    DECLARE @payment2 uniqueidentifier=NEWID(), @compensation uniqueidentifier=NEWID();
    INSERT dbo.tc_payments(id,order_id,txn_ref,amount,status,captured_at) VALUES(@payment2,@order,CONCAT('D15-',@payment2),400000,'CAPTURED',@now);
    INSERT dbo.tc_refunds(id,payment_id,purpose,amount) VALUES(@compensation,@payment2,'PAYMENT_COMPENSATION',400000);
    EXEC dbo.usp_ApplyRefundResult @compensation,N'{"status":"SUCCEEDED","providerReference":"compensation"}';
    IF (SELECT sold_quantity FROM dbo.tc_zones WHERE id=@zone)<>0 THROW 51908,'compensation changed inventory',1;

    UPDATE dbo.tc_tickets SET status='REFUNDED' WHERE id IN(@ticket1,@ticket2);
    BEGIN TRY
      UPDATE dbo.tc_tickets SET paid_amount=paid_amount+1 WHERE id IN(@ticket1,@ticket2);
      THROW 51909,'TR08 accepted immutable paid amount',1;
    END TRY BEGIN CATCH IF ERROR_NUMBER()=51909 THROW; END CATCH;
    IF (SELECT SUM(paid_amount) FROM dbo.tc_tickets WHERE id IN(@ticket1,@ticket2))<>400000 THROW 51910,'TR08 did not rollback multirow update',1;

    DECLARE @zeroTicket uniqueidentifier=NEWID(), @zeroRequest uniqueidentifier=NEWID();
    UPDATE dbo.tc_zones SET sold_quantity=1 WHERE id=@zone;
    INSERT dbo.tc_tickets(id,order_item_id,ticket_code,qr_secret_hash,paid_amount,status) VALUES(@zeroTicket,@item,CONCAT('D15-',@zeroTicket),0x03,0,'ACTIVE');
    INSERT dbo.tc_refund_requests(id,order_id,requester_id,reason,reason_type) VALUES(@zeroRequest,@order,@buyer,N'Zero','CUSTOMER_REQUEST');
    INSERT dbo.tc_refund_request_tickets(refund_request_id,ticket_id) VALUES(@zeroRequest,@zeroTicket);
    EXEC dbo.usp_ReviewRefundRequest @zeroRequest,@admin,'APPROVE',NULL,0;
    IF (SELECT status FROM dbo.tc_refund_requests WHERE id=@zeroRequest)<>'COMPLETED' OR EXISTS(SELECT 1 FROM dbo.tc_refunds WHERE refund_request_id=@zeroRequest)
      THROW 51911,'zero refund did not complete without refund row',1;

    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
