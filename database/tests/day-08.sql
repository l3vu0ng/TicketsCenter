SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
BEGIN TRY
    DECLARE @user uniqueidentifier = NEWID(), @org uniqueidentifier = NEWID(), @cat uniqueidentifier = NEWID(),
            @event uniqueidentifier = NEWID(), @zone uniqueidentifier = NEWID(), @hold uniqueidentifier,
            @hold2 uniqueidentifier, @now datetime2(3) = SYSUTCDATETIME(), @items nvarchar(max);
    INSERT dbo.tc_users(id,email,normalized_email,password_hash,full_name,email_verified_at)
    VALUES (@user, 'day08-' + CONVERT(varchar(36),@user) + '@example.test', 'day08-' + CONVERT(varchar(36),@user) + '@example.test', 'test', 'Day 08', @now);
    INSERT dbo.tc_organizations(id,name,contact_email) VALUES (@org,'Day 08 Org','day08@example.test');
    INSERT dbo.tc_event_categories(id,name,slug) VALUES (@cat,'Day 08','day-08-' + CONVERT(varchar(36),@cat));
    INSERT dbo.tc_events(id,organization_id,category_id,title,venue_name,venue_address,sale_start,sale_end,start_time,end_time,status)
    VALUES (@event,@org,@cat,'Hold test','Venue','Address',DATEADD(minute,-1,@now),DATEADD(hour,1,@now),DATEADD(hour,2,@now),DATEADD(hour,3,@now),'DRAFT');
    INSERT dbo.tc_zones(id,event_id,name,type,price,capacity) VALUES (@zone,@event,'Standing','STANDING',100000,5);
    UPDATE dbo.tc_events SET status = 'PUBLISHED' WHERE id = @event;

    SET @items = N'[{"zoneId":"' + CONVERT(nvarchar(36),@zone) + N'","seatId":null,"quantity":3}]';
    DECLARE @created TABLE (hold_id uniqueidentifier, expires_at datetime2(3));
    INSERT @created EXEC dbo.usp_CreateTicketHold @user,@event,@items;
    SELECT @hold = hold_id FROM @created;
    IF (SELECT held_quantity FROM dbo.tc_zones WHERE id=@zone) <> 3 THROW 51801, 'hold did not reserve quota', 1;
    EXEC dbo.usp_ReleaseTicketHold @hold,@user,'USER_CANCEL';
    IF EXISTS (SELECT 1 FROM dbo.tc_ticket_holds WHERE id=@hold AND status <> 'RELEASED') THROW 51802, 'cancel did not release hold', 1;
    IF (SELECT held_quantity FROM dbo.tc_zones WHERE id=@zone) <> 0 THROW 51803, 'cancel did not release quota', 1;

    INSERT dbo.tc_ticket_holds(id,user_id,event_id,status,created_at,expires_at)
    VALUES (NEWID(),@user,@event,'ACTIVE',DATEADD(minute,-11,@now),DATEADD(minute,-1,@now));
    DECLARE @expired uniqueidentifier = (SELECT TOP 1 id FROM dbo.tc_ticket_holds WHERE user_id=@user AND status='ACTIVE');
    SET @items = N'[{"zoneId":"' + CONVERT(nvarchar(36),@zone) + N'","seatId":null,"quantity":1}]';
    DELETE FROM @created;
    INSERT @created EXEC dbo.usp_CreateTicketHold @user,@event,@items;
    SELECT @hold2 = hold_id FROM @created;
    IF EXISTS (SELECT 1 FROM dbo.tc_ticket_holds WHERE id=@expired AND status <> 'RELEASED') THROW 51804, 'expired hold was not reclaimed', 1;
    IF (SELECT held_quantity FROM dbo.tc_zones WHERE id=@zone) <> 1 THROW 51805, 'new hold did not reserve quota', 1;
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
