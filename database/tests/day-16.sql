SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
BEGIN TRY
    DECLARE @now datetime2(3) = SYSUTCDATETIME(),
            @admin uniqueidentifier = NEWID(), @manager uniqueidentifier = NEWID(), @buyer uniqueidentifier = NEWID(),
            @org uniqueidentifier = NEWID(), @category uniqueidentifier = NEWID(),
            @event uniqueidentifier = NEWID(), @zone uniqueidentifier = NEWID(), @hold uniqueidentifier = NEWID(),
            @order uniqueidentifier = NEWID(), @item uniqueidentifier = NEWID(), @payment uniqueidentifier = NEWID(),
            @ticket1 uniqueidentifier = NEWID(), @ticket2 uniqueidentifier = NEWID(), @ticket3 uniqueidentifier = NEWID(),
            @request1 uniqueidentifier = NEWID();

    INSERT dbo.tc_users(id,email,normalized_email,password_hash,full_name,email_verified_at) VALUES
        (@admin, CONCAT('day16-admin-',@admin,'@example.test'), CONCAT('day16-admin-',@admin,'@example.test'), 'test', 'Day 16 Admin', @now),
        (@manager, CONCAT('day16-manager-',@manager,'@example.test'), CONCAT('day16-manager-',@manager,'@example.test'), 'test', 'Day 16 Manager', @now),
        (@buyer, CONCAT('day16-buyer-',@buyer,'@example.test'), CONCAT('day16-buyer-',@buyer,'@example.test'), 'test', 'Day 16 Buyer', @now);
    INSERT dbo.tc_user_platform_roles(user_id,role) VALUES(@admin,'ADMIN');
    INSERT dbo.tc_organizations(id,name,contact_email) VALUES(@org,'Day 16 Org','day16@example.test');
    INSERT dbo.tc_event_categories(id,name,slug) VALUES(@category,'Day 16',CONCAT('day-16-',@category));
    INSERT dbo.tc_events(id,organization_id,category_id,title,venue_name,venue_address,sale_start,sale_end,start_time,end_time,status)
    VALUES(@event,@org,@category,'Cancellation','Venue','Address',DATEADD(day,-1,@now),DATEADD(hour,1,@now),DATEADD(hour,2,@now),DATEADD(hour,3,@now),'PUBLISHED');
    INSERT dbo.tc_zones(id,event_id,name,type,price,capacity,sold_quantity) VALUES(@zone,@event,'Standing','STANDING',100,10,3);
    INSERT dbo.tc_ticket_holds(id,user_id,event_id,status,expires_at) VALUES(@hold,@buyer,@event,'CONSUMED',DATEADD(minute,10,@now));
    INSERT dbo.tc_orders(id,user_id,event_id,hold_id,order_code,subtotal_amount,discount_amount,total_amount,status,paid_at)
    VALUES(@order,@buyer,@event,@hold,CONCAT('D16-',@order),300,0,300,'PAID',@now);
    INSERT dbo.tc_order_items(id,order_id,zone_id,quantity,unit_price,zone_name_snapshot)
    VALUES(@item,@order,@zone,3,100,'Standing');
    INSERT dbo.tc_payments(id,order_id,txn_ref,amount,status,captured_at)
    VALUES(@payment,@order,CONCAT('D16-',@payment),300,'CAPTURED',@now);
    INSERT dbo.tc_tickets(id,order_item_id,ticket_code,qr_secret_hash,paid_amount,status) VALUES
        (@ticket1,@item,CONCAT('D16-',@ticket1),0x01,100,'REFUND_PENDING'),
        (@ticket2,@item,CONCAT('D16-',@ticket2),0x02,100,'ACTIVE'),
        (@ticket3,@item,CONCAT('D16-',@ticket3),0x03,100,'USED');
    INSERT dbo.tc_refund_requests(id,order_id,requester_id,reason,reason_type)
    VALUES(@request1,@order,@buyer,N'Customer request','CUSTOMER_REQUEST');
    INSERT dbo.tc_refund_request_tickets(refund_request_id,ticket_id) VALUES(@request1,@ticket1);

    EXEC dbo.usp_CancelEvent @event,@admin;
    IF (SELECT status FROM dbo.tc_events WHERE id=@event)<>'CANCELLED' THROW 51960,'event was not cancelled',1;
    IF (SELECT COUNT(*) FROM dbo.tc_outbox WHERE idempotency_key=CONCAT('event-cancelled:',@event))<>1 THROW 51961,'cancellation work missing',1;
    IF (SELECT COUNT(*) FROM dbo.tc_audit_logs WHERE aggregate_id=@event AND action='EVENT_STATUS_CHANGED')<>1 THROW 51962,'status audit missing',1;
    EXEC dbo.usp_CancelEvent @event,@admin;
    IF (SELECT COUNT(*) FROM dbo.tc_outbox WHERE idempotency_key=CONCAT('event-cancelled:',@event))<>1 THROW 51963,'repeat cancel duplicated work',1;

    BEGIN TRY
        DECLARE @selection nvarchar(max)=CONCAT(N'[{"zoneId":"',@zone,N'","seatId":null,"quantity":1}]');
        EXEC dbo.usp_CreateTicketHold @buyer,@event,@selection;
        THROW 51964,'cancelled event accepted a new hold',1;
    END TRY
    BEGIN CATCH
        IF ERROR_NUMBER()=51964 THROW;
    END CATCH;

    DECLARE @scan TABLE(result varchar(30),scanned_at datetime2(3),ticket_id uniqueidentifier);
    INSERT @scan EXEC dbo.usp_CheckInTicket @event,@manager,CONCAT('D16-',@ticket2);
    IF NOT EXISTS(SELECT 1 FROM @scan WHERE result='EVENT_CANCELLED') THROW 51965,'cancelled event accepted check-in',1;

    EXEC dbo.usp_ProcessCancelledOrder @event,@order;
    IF EXISTS(SELECT 1 FROM dbo.tc_refund_requests WHERE order_id=@order AND reason_type<>'EVENT_CANCELLATION') THROW 51966,'request reason was not converted',1;
    IF (SELECT COUNT(*) FROM dbo.tc_refund_requests WHERE order_id=@order)<>2 THROW 51967,'partial request did not create remaining obligation',1;
    IF (SELECT COUNT(*) FROM dbo.tc_refunds r JOIN dbo.tc_refund_requests rr ON rr.id=r.refund_request_id WHERE rr.order_id=@order)<>2 THROW 51968,'refund obligations were not created exactly once',1;
    EXEC dbo.usp_ProcessCancelledOrder @event,@order;
    IF (SELECT COUNT(*) FROM dbo.tc_refunds r JOIN dbo.tc_refund_requests rr ON rr.id=r.refund_request_id WHERE rr.order_id=@order)<>2 THROW 51969,'retry duplicated refund',1;
    IF NOT EXISTS(SELECT 1 FROM dbo.vw_EventCancellationExceptions WHERE event_id=@event AND exception_id=@ticket3 AND exception_type='USED_TICKET') THROW 51970,'used ticket exception missing',1;

    DECLARE @refund uniqueidentifier=(SELECT TOP(1) r.id FROM dbo.tc_refunds r JOIN dbo.tc_refund_requests rr ON rr.id=r.refund_request_id WHERE rr.order_id=@order);
    UPDATE dbo.tc_refunds SET status='UNKNOWN' WHERE id=@refund;
    IF (SELECT exception_count FROM dbo.vw_EventCancellationProgress WHERE event_id=@event)<2 THROW 51971,'unknown refund was not reported',1;
    IF (SELECT completed_orders FROM dbo.vw_EventCancellationProgress WHERE event_id=@event)<>0 THROW 51972,'progress completed an order with exceptions',1;

    DECLARE @raceHold uniqueidentifier=NEWID(), @raceOrder uniqueidentifier=NEWID(),
            @raceItem uniqueidentifier=NEWID(), @racePayment uniqueidentifier=NEWID();
    UPDATE dbo.tc_zones SET held_quantity=held_quantity+1 WHERE id=@zone;
    INSERT dbo.tc_ticket_holds(id,user_id,event_id,status,expires_at) VALUES(@raceHold,@buyer,@event,'ACTIVE',DATEADD(minute,10,@now));
    INSERT dbo.tc_ticket_hold_items(hold_id,zone_id,quantity,unit_price) VALUES(@raceHold,@zone,1,100);
    INSERT dbo.tc_orders(id,user_id,event_id,hold_id,order_code,subtotal_amount,discount_amount,total_amount,status)
    VALUES(@raceOrder,@buyer,@event,@raceHold,CONCAT('D16-',@raceOrder),100,0,100,'PENDING_PAYMENT');
    INSERT dbo.tc_order_items(id,order_id,zone_id,quantity,unit_price,zone_name_snapshot)
    VALUES(@raceItem,@raceOrder,@zone,1,100,'Standing');
    INSERT dbo.tc_payments(id,order_id,txn_ref,amount,status)
    VALUES(@racePayment,@raceOrder,CONCAT('D16-',@racePayment),100,'PENDING');
    EXEC dbo.usp_ApplyPaymentResult @raceOrder,NULL,@racePayment,N'{"status":"CAPTURED","providerReference":"late"}',N'[]',0;
    IF NOT EXISTS(SELECT 1 FROM dbo.tc_refunds WHERE payment_id=@racePayment AND purpose='PAYMENT_COMPENSATION' AND status='PENDING') THROW 51980,'late payment did not create compensation',1;
    IF EXISTS(SELECT 1 FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id=t.order_item_id WHERE oi.order_id=@raceOrder) THROW 51981,'late payment issued active tickets',1;
    EXEC dbo.usp_ProcessCancelledOrder @event,@raceOrder;
    IF (SELECT status FROM dbo.tc_ticket_holds WHERE id=@raceHold)<>'RELEASED' THROW 51982,'late payment hold was not released',1;

    DECLARE @rejectHold uniqueidentifier=NEWID(), @rejectOrder uniqueidentifier=NEWID(),
            @rejectItem uniqueidentifier=NEWID(), @rejectTicket uniqueidentifier=NEWID(),
            @rejectPayment uniqueidentifier=NEWID(), @rejectRequest uniqueidentifier=NEWID();
    INSERT dbo.tc_ticket_holds(id,user_id,event_id,status,expires_at) VALUES(@rejectHold,@buyer,@event,'CONSUMED',DATEADD(minute,10,@now));
    INSERT dbo.tc_orders(id,user_id,event_id,hold_id,order_code,subtotal_amount,discount_amount,total_amount,status,paid_at)
    VALUES(@rejectOrder,@buyer,@event,@rejectHold,CONCAT('D16-',@rejectOrder),100,0,100,'PAID',@now);
    INSERT dbo.tc_order_items(id,order_id,zone_id,quantity,unit_price,zone_name_snapshot)
    VALUES(@rejectItem,@rejectOrder,@zone,1,100,'Standing');
    INSERT dbo.tc_payments(id,order_id,txn_ref,amount,status,captured_at)
    VALUES(@rejectPayment,@rejectOrder,CONCAT('D16-',@rejectPayment),100,'CAPTURED',@now);
    INSERT dbo.tc_tickets(id,order_item_id,ticket_code,qr_secret_hash,paid_amount,status)
    VALUES(@rejectTicket,@rejectItem,CONCAT('D16-',@rejectTicket),0x05,100,'REFUND_PENDING');
    INSERT dbo.tc_refund_requests(id,order_id,requester_id,reason,reason_type)
    VALUES(@rejectRequest,@rejectOrder,@buyer,N'Before cancellation','CUSTOMER_REQUEST');
    INSERT dbo.tc_refund_request_tickets(refund_request_id,ticket_id) VALUES(@rejectRequest,@rejectTicket);
    EXEC dbo.usp_ReviewRefundRequest @rejectRequest,@admin,'REJECT',N'Race decision',0;
    IF (SELECT status FROM dbo.tc_tickets WHERE id=@rejectTicket)<>'INVALIDATED' THROW 51983,'reject restored ticket for cancelled event',1;

    DECLARE @rollbackEvent uniqueidentifier=NEWID();
    INSERT dbo.tc_events(id,organization_id,category_id,title,venue_name,venue_address,sale_start,sale_end,start_time,end_time,status)
    VALUES(@rollbackEvent,@org,@category,'Rollback cancel','Venue','Address',DATEADD(day,-1,@now),DATEADD(hour,1,@now),DATEADD(hour,2,@now),DATEADD(hour,3,@now),'PUBLISHED');
    INSERT dbo.tc_outbox(event_type,aggregate_type,aggregate_id,payload,idempotency_key)
    VALUES('TEST','EVENT',@rollbackEvent,N'{}',CONCAT('event-cancelled:',@rollbackEvent));
    BEGIN TRY
        EXEC dbo.usp_CancelEvent @rollbackEvent,@admin;
        THROW 51973,'TX13 failure was not raised',1;
    END TRY
    BEGIN CATCH
        IF ERROR_NUMBER()=51973 THROW;
    END CATCH;
    IF (SELECT status FROM dbo.tc_events WHERE id=@rollbackEvent)<>'PUBLISHED' THROW 51974,'TX13 did not rollback event status',1;

    DECLARE @rollbackHold uniqueidentifier=NEWID(), @rollbackOrder uniqueidentifier=NEWID(),
            @rollbackItem uniqueidentifier=NEWID(), @rollbackTicket uniqueidentifier=NEWID();
    INSERT dbo.tc_ticket_holds(id,user_id,event_id,status,expires_at) VALUES(@rollbackHold,@buyer,@event,'CONSUMED',DATEADD(minute,10,@now));
    INSERT dbo.tc_orders(id,user_id,event_id,hold_id,order_code,subtotal_amount,discount_amount,total_amount,status,paid_at)
    VALUES(@rollbackOrder,@buyer,@event,@rollbackHold,CONCAT('D16-',@rollbackOrder),100,0,100,'PAID',@now);
    INSERT dbo.tc_order_items(id,order_id,zone_id,quantity,unit_price,zone_name_snapshot)
    VALUES(@rollbackItem,@rollbackOrder,@zone,1,100,'Standing');
    INSERT dbo.tc_tickets(id,order_item_id,ticket_code,qr_secret_hash,paid_amount,status)
    VALUES(@rollbackTicket,@rollbackItem,CONCAT('D16-',@rollbackTicket),0x04,100,'ACTIVE');
    BEGIN TRY
        EXEC dbo.usp_ProcessCancelledOrder @event,@rollbackOrder;
        THROW 51975,'TX17 failure was not raised',1;
    END TRY
    BEGIN CATCH
        IF ERROR_NUMBER()=51975 THROW;
    END CATCH;
    IF (SELECT status FROM dbo.tc_tickets WHERE id=@rollbackTicket)<>'ACTIVE' THROW 51976,'TX17 did not rollback ticket',1;
    IF EXISTS(SELECT 1 FROM dbo.tc_refund_requests WHERE order_id=@rollbackOrder) THROW 51977,'TX17 left a partial request',1;

    DECLARE @startedEvent uniqueidentifier=NEWID();
    INSERT dbo.tc_events(id,organization_id,category_id,title,venue_name,venue_address,sale_start,sale_end,start_time,end_time,status)
    VALUES(@startedEvent,@org,@category,'Started','Venue','Address',DATEADD(day,-1,@now),@now,@now,DATEADD(hour,1,@now),'PUBLISHED');
    BEGIN TRY
        EXEC dbo.usp_CancelEvent @startedEvent,@admin;
        THROW 51984,'event at start time was cancelled',1;
    END TRY
    BEGIN CATCH
        IF ERROR_NUMBER()=51984 THROW;
    END CATCH;
    BEGIN TRY
        EXEC dbo.usp_CancelEvent @startedEvent,@manager;
        THROW 51985,'manager cancelled event',1;
    END TRY
    BEGIN CATCH
        IF ERROR_NUMBER()=51985 THROW;
    END CATCH;

    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
