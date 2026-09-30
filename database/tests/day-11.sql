SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
BEGIN TRY
    DECLARE @user uniqueidentifier=NEWID(), @org uniqueidentifier=NEWID(), @cat uniqueidentifier=NEWID(),
            @event uniqueidentifier=NEWID(), @zone uniqueidentifier=NEWID(), @hold uniqueidentifier,
            @order uniqueidentifier, @now datetime2(3)=SYSUTCDATETIME(), @items nvarchar(max), @code varchar(100)='DAY11-SECURE-CODE';
    INSERT dbo.tc_users(id,email,normalized_email,password_hash,full_name,email_verified_at)
    VALUES(@user,'day11-'+CONVERT(varchar(36),@user)+'@example.test','day11-'+CONVERT(varchar(36),@user)+'@example.test','test','Day 11',@now);
    INSERT dbo.tc_organizations(id,name,contact_email) VALUES(@org,'Day 11 Org','day11@example.test');
    INSERT dbo.tc_event_categories(id,name,slug) VALUES(@cat,'Day 11','day-11-'+CONVERT(varchar(36),@cat));
    INSERT dbo.tc_events(id,organization_id,category_id,title,venue_name,venue_address,sale_start,sale_end,start_time,end_time,status)
    VALUES(@event,@org,@cat,'Payment test','Venue','Address',DATEADD(minute,-1,@now),DATEADD(hour,1,@now),DATEADD(hour,2,@now),DATEADD(hour,3,@now),'DRAFT');
    INSERT dbo.tc_zones(id,event_id,name,type,price,capacity) VALUES(@zone,@event,'Free','STANDING',0,2);
    UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id=@event;
    SET @items=N'[{"zoneId":"'+CONVERT(nvarchar(36),@zone)+N'","seatId":null,"quantity":1}]';
    DECLARE @created TABLE(hold_id uniqueidentifier,expires_at datetime2(3));
    INSERT @created EXEC dbo.usp_CreateTicketHold @user,@event,@items;
    SELECT @hold=hold_id FROM @created;
    DECLARE @orders TABLE(order_id uniqueidentifier,order_code varchar(64),subtotal_amount decimal(19,0),discount_amount decimal(19,0),total_amount decimal(19,0));
    INSERT @orders EXEC dbo.usp_CreateOrderFromHold @hold,@user;
    SELECT @order=order_id FROM @orders;
    DECLARE @hash varchar(128)=CONVERT(varchar(128),HASHBYTES('SHA2_256',@code),2);
    DECLARE @verified nvarchar(max)=N'{"status":"CAPTURED","providerReference":"DAY11-TEST"}',
            @ticketCodes nvarchar(max)=N'[{"ticketCode":"'+@code+N'","qrSecretHashHex":"'+@hash+N'"}]';
    EXEC dbo.usp_ApplyPaymentResult @order,NULL,NULL,@verified,@ticketCodes,0;
    IF (SELECT status FROM dbo.tc_orders WHERE id=@order)<>'PAID' THROW 51911,'order was not paid',1;
    IF NOT EXISTS(SELECT 1 FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id=t.order_item_id WHERE oi.order_id=@order AND t.ticket_code=@code AND t.paid_amount=0) THROW 51912,'ticket was not issued',1;
    IF (SELECT held_quantity FROM dbo.tc_zones WHERE id=@zone)<>0 OR (SELECT sold_quantity FROM dbo.tc_zones WHERE id=@zone)<>1 THROW 51913,'inventory was not committed',1;
    IF (SELECT status FROM dbo.tc_ticket_holds WHERE id=@hold)<>'CONSUMED' THROW 51914,'hold was not consumed',1;
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
