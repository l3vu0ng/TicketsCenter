SET NOCOUNT ON;
SET XACT_ABORT OFF;

BEGIN TRANSACTION;
BEGIN TRY
    DECLARE @admin uniqueidentifier = NEWID(), @organization uniqueidentifier = NEWID(),
            @category uniqueidentifier = NEWID(), @rule uniqueidentifier = NEWID(),
            @event uniqueidentifier = NEWID(), @seated uniqueidentifier = NEWID(),
            @standing uniqueidentifier = NEWID(), @now datetime2(3) = SYSUTCDATETIME();

    INSERT dbo.tc_users(id, email, normalized_email, password_hash, full_name)
    VALUES (@admin, N'day07-admin@example.test', N'day07-admin@example.test', N'test', N'Day 07 Admin');
    INSERT dbo.tc_user_platform_roles(user_id, role) VALUES (@admin, 'ADMIN');
    INSERT dbo.tc_organizations(id, name, contact_email) VALUES (@organization, N'Day 07 Org', N'day07@example.test');
    INSERT dbo.tc_event_categories(id, name, slug) VALUES (@category, N'Day 07', 'day-07');
    INSERT dbo.tc_commission_rules(id, organization_id, rate_percent, fixed_fee, effective_from, effective_to)
    VALUES (@rule, @organization, 5, 0, DATEADD(day, -1, @now), DATEADD(day, 1, @now));
    INSERT dbo.tc_events(id, organization_id, category_id, title, cover_image_url, venue_name, venue_address,
                         sale_start, sale_end, start_time, end_time, status)
    VALUES (@event, @organization, @category, N'Day 07 Event', N'/api/event-images/test.png', N'Nhà hát', N'Hà Nội',
            DATEADD(hour, 1, @now), DATEADD(day, 1, @now), DATEADD(day, 2, @now), DATEADD(day, 3, @now), 'PENDING_APPROVAL');
    INSERT dbo.tc_zones(id, event_id, name, type, price, capacity)
    VALUES (@seated, @event, N'Ghế', 'SEATED', 0, NULL),
           (@standing, @event, N'Đứng', 'STANDING', 200000, 10);
    INSERT dbo.tc_seats(zone_id, row_name, seat_number, label) VALUES (@seated, N'A', 1, N'A1');

    EXEC sys.sp_set_session_context @key=N'actor_id', @value=@admin;
    EXEC dbo.usp_PublishEvent @event, @admin, @rule;

    IF NOT EXISTS (SELECT 1 FROM dbo.vw_PublicEvents WHERE event_id = @event)
        THROW 51710, N'Published event is missing from V01', 1;
    IF (SELECT status FROM dbo.tc_events WHERE id = @event) <> 'PUBLISHED'
        THROW 51711, N'SP12 did not publish the event', 1;
    IF (SELECT COUNT_BIG(*) FROM dbo.tc_audit_logs WHERE aggregate_id = @event AND action = 'EVENT_STATUS_CHANGED') <> 1
        THROW 51712, N'Publish did not write exactly one status audit', 1;

    UPDATE dbo.tc_zones SET price = price + 1000 WHERE id IN (@seated, @standing);
    IF @@ROWCOUNT <> 2 THROW 51713, N'Published zone price update should remain allowed', 1;

    DECLARE @blocked bit = 0;
    BEGIN TRY
        UPDATE dbo.tc_zones SET capacity = CASE WHEN id = @standing THEN 20 ELSE capacity END,
                                name = CASE WHEN id = @seated THEN N'Đổi tên' ELSE name END
        WHERE id IN (@seated, @standing);
    END TRY
    BEGIN CATCH
        IF ERROR_NUMBER() <> 51101 THROW;
        SET @blocked = 1;
    END CATCH;
    IF @blocked = 0 THROW 51714, N'TR01 allowed a multi-row published layout change', 1;
    IF EXISTS (SELECT 1 FROM dbo.tc_zones WHERE id = @standing AND capacity <> 10)
        THROW 51715, N'Failed multi-row layout update was not atomic', 1;

    SET @blocked = 0;
    BEGIN TRY
        INSERT dbo.tc_seats(zone_id, row_name, seat_number, label) VALUES (@seated, N'A', 2, N'A2');
    END TRY
    BEGIN CATCH
        IF ERROR_NUMBER() <> 51102 THROW;
        SET @blocked = 1;
    END CATCH;
    IF @blocked = 0 THROW 51716, N'TR02 allowed a seat after publish', 1;

    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
