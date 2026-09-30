SET NOCOUNT ON;
SET XACT_ABORT OFF;

BEGIN TRANSACTION;
BEGIN TRY
    DECLARE @user uniqueidentifier = NEWID(), @other uniqueidentifier = NEWID(), @org uniqueidentifier = NEWID(),
            @category uniqueidentifier = NEWID(), @event uniqueidentifier = NEWID(), @zone uniqueidentifier = NEWID(),
            @hold uniqueidentifier = NEWID(), @coupon uniqueidentifier = NEWID(), @now datetime2(3) = SYSUTCDATETIME(),
            @order uniqueidentifier, @order2 uniqueidentifier, @discount decimal(19,0);

    INSERT dbo.tc_users(id, email, normalized_email, password_hash, full_name, email_verified_at)
    VALUES (@user, N'day09-user@example.test', N'day09-user@example.test', N'test', N'Day 09 User', @now),
           (@other, N'day09-other@example.test', N'day09-other@example.test', N'test', N'Day 09 Other', @now);
    INSERT dbo.tc_organizations(id, name, contact_email) VALUES (@org, N'Day 09 Org', N'day09@example.test');
    INSERT dbo.tc_event_categories(id, name, slug) VALUES (@category, N'Day 09', CONCAT('day-09-', CONVERT(varchar(36), @category)));
    INSERT dbo.tc_events(id, organization_id, category_id, title, venue_name, venue_address,
                         sale_start, sale_end, start_time, end_time, status)
    VALUES (@event, @org, @category, N'Day 09 Event', N'Venue', N'Hà Nội', DATEADD(hour, -1, @now),
            DATEADD(day, 1, @now), DATEADD(day, 2, @now), DATEADD(day, 3, @now), 'DRAFT');
    INSERT dbo.tc_zones(id, event_id, name, type, price, capacity) VALUES (@zone, @event, N'Đứng', 'STANDING', 500000, 3);
    UPDATE dbo.tc_events SET status = 'PUBLISHED' WHERE id = @event;
    INSERT dbo.tc_ticket_holds(id, user_id, event_id, status, created_at, expires_at)
    VALUES (@hold, @user, @event, 'ACTIVE', @now, DATEADD(minute, 10, @now));
    INSERT dbo.tc_ticket_hold_items(hold_id, zone_id, quantity, unit_price)
    VALUES (@hold, @zone, 1, 500000);
    INSERT dbo.tc_coupons(id, organization_id, code, discount_type, fixed_amount, max_uses, valid_from, valid_to)
    VALUES (@coupon, @org, 'SAVE', 'FIXED_AMOUNT', 200000, 1, DATEADD(day, -1, @now), DATEADD(day, 1, @now));

    DECLARE @created TABLE(order_id uniqueidentifier, order_code varchar(64), subtotal decimal(19,0), discount decimal(19,0), total decimal(19,0));
    INSERT @created EXEC dbo.usp_CreateOrderFromHold @hold, @user;
    SELECT @order = order_id FROM @created;
    DELETE FROM @created;
    INSERT @created EXEC dbo.usp_CreateOrderFromHold @hold, @user;
    IF (SELECT COUNT(*) FROM @created WHERE order_id = @order) <> 1 THROW 51901, N'SP06 replay did not return the same order', 1;
    IF (SELECT unit_price FROM dbo.tc_order_items WHERE order_id = @order) <> 500000
        THROW 51902, N'Order item did not snapshot Hold price', 1;

    UPDATE dbo.tc_zones SET price = 900000 WHERE id = @zone;
    DECLARE @couponResult TABLE(subtotal decimal(19,0), discount decimal(19,0), total decimal(19,0), coupon_id uniqueidentifier);
    INSERT @couponResult EXEC dbo.usp_ApplyOrderCoupon @order, @user, 'SAVE';
    SELECT @discount = discount FROM @couponResult;
    IF @discount <> 150000 OR (SELECT total_amount FROM dbo.tc_orders WHERE id = @order) <> 350000
        THROW 51903, N'SP03 did not apply the 30 percent cap', 1;
    DELETE FROM @couponResult;
    INSERT @couponResult EXEC dbo.usp_ApplyOrderCoupon @order, @user, NULL;
    IF EXISTS (SELECT 1 FROM dbo.tc_orders WHERE id = @order AND (coupon_id IS NOT NULL OR discount_amount <> 0 OR total_amount <> 500000))
        THROW 51904, N'SP03 did not remove coupon and restore subtotal', 1;

    DECLARE @blocked bit = 0;
    BEGIN TRY
        EXEC dbo.usp_CreateOrderFromHold @hold, @other;
    END TRY
    BEGIN CATCH
        IF ERROR_NUMBER() <> 51260 THROW;
        SET @blocked = 1;
    END CATCH;
    IF @blocked = 0 THROW 51905, N'Owner check did not reject another buyer', 1;

    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
