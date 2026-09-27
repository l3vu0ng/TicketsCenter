SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @passwordHash nvarchar(255) = CONVERT(nvarchar(255), SESSION_CONTEXT(N'test_password_hash'));
IF @passwordHash IS NULL OR LEN(@passwordHash) = 0
    THROW 51000, N'Missing test_password_hash session context', 1;

DECLARE @now datetime2(3) = SYSUTCDATETIME();

BEGIN TRANSACTION;
BEGIN TRY
    IF NOT EXISTS (SELECT 1 FROM dbo.tc_users WHERE id = '10000000-0000-0000-0000-000000000001')
        INSERT dbo.tc_users(id, email, normalized_email, password_hash, full_name, email_verified_at)
        VALUES ('10000000-0000-0000-0000-000000000001', N'manager.a@example.test', N'manager.a@example.test', @passwordHash, N'Quản lý A', @now);

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_users WHERE id = '10000000-0000-0000-0000-000000000002')
        INSERT dbo.tc_users(id, email, normalized_email, password_hash, full_name, email_verified_at)
        VALUES ('10000000-0000-0000-0000-000000000002', N'manager.b@example.test', N'manager.b@example.test', @passwordHash, N'Quản lý B', @now);

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_users WHERE id = '10000000-0000-0000-0000-000000000003')
        INSERT dbo.tc_users(id, email, normalized_email, password_hash, full_name)
        VALUES ('10000000-0000-0000-0000-000000000003', N'unverified.buyer@example.test', N'unverified.buyer@example.test', @passwordHash, N'Người mua chưa xác minh');

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_users WHERE id = '10000000-0000-0000-0000-000000000004')
        INSERT dbo.tc_users(id, email, normalized_email, password_hash, full_name, status, email_verified_at)
        VALUES ('10000000-0000-0000-0000-000000000004', N'disabled.buyer@example.test', N'disabled.buyer@example.test', @passwordHash, N'Người mua đã khóa', 'DISABLED', @now);

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_organizations WHERE id = '10000000-0000-0000-0000-000000000010')
        INSERT dbo.tc_organizations(id, name, contact_email, contact_phone, description)
        VALUES ('10000000-0000-0000-0000-000000000010', N'Tổ chức Ánh Dương', N'contact-a@example.test', N'0000000001', N'Fixture tổ chức A');

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_organizations WHERE id = '10000000-0000-0000-0000-000000000020')
        INSERT dbo.tc_organizations(id, name, contact_email, contact_phone, description)
        VALUES ('10000000-0000-0000-0000-000000000020', N'Tổ chức Bình Minh', N'contact-b@example.test', N'0000000002', N'Fixture tổ chức B');

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_organization_memberships WHERE id = '10000000-0000-0000-0000-000000000011')
        INSERT dbo.tc_organization_memberships(id, user_id, organization_id, role)
        VALUES ('10000000-0000-0000-0000-000000000011', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000010', 'MANAGER');

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_organization_memberships WHERE id = '10000000-0000-0000-0000-000000000012')
        INSERT dbo.tc_organization_memberships(id, user_id, organization_id, role)
        VALUES ('10000000-0000-0000-0000-000000000012', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000020', 'CHECK_IN_STAFF');

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_organization_memberships WHERE id = '10000000-0000-0000-0000-000000000021')
        INSERT dbo.tc_organization_memberships(id, user_id, organization_id, role)
        VALUES ('10000000-0000-0000-0000-000000000021', '10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000020', 'MANAGER');

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = '10000000-0000-0000-0000-000000000002' AND role = 'ADMIN')
        INSERT dbo.tc_user_platform_roles(user_id, role)
        VALUES ('10000000-0000-0000-0000-000000000002', 'ADMIN');

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_event_categories WHERE id = '10000000-0000-0000-0000-000000000030')
        INSERT dbo.tc_event_categories(id, name, slug)
        VALUES ('10000000-0000-0000-0000-000000000030', N'Âm nhạc', 'fixture-music');

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_commission_rules WHERE id = '10000000-0000-0000-0000-000000000031')
        INSERT dbo.tc_commission_rules(id, organization_id, rate_percent, fixed_fee, effective_from, effective_to)
        VALUES ('10000000-0000-0000-0000-000000000031', '10000000-0000-0000-0000-000000000010', 5, 1000, DATEADD(day, -30, @now), DATEADD(day, 365, @now));

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_commission_rules WHERE id = '10000000-0000-0000-0000-000000000032')
        INSERT dbo.tc_commission_rules(id, organization_id, rate_percent, fixed_fee, effective_from, effective_to)
        VALUES ('10000000-0000-0000-0000-000000000032', '10000000-0000-0000-0000-000000000020', 6, 0, DATEADD(day, -30, @now), DATEADD(day, 365, @now));

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WHERE id = '10000000-0000-0000-0000-000000000101')
        INSERT dbo.tc_events(id, organization_id, category_id, commission_rule_id, title, description, cover_image_url, venue_name, venue_address, sale_start, sale_end, start_time, end_time, status)
        VALUES ('10000000-0000-0000-0000-000000000101', '10000000-0000-0000-0000-000000000010', '10000000-0000-0000-0000-000000000030', '10000000-0000-0000-0000-000000000031', N'Đêm nhạc Việt', N'Sự kiện fixture', N'https://example.test/a.jpg', N'Nhà hát A', N'Hà Nội', DATEADD(day,-1,@now), DATEADD(day,20,@now), DATEADD(day,30,@now), DATEADD(day,31,@now), 'PUBLISHED');

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WHERE id = '10000000-0000-0000-0000-000000000102')
        INSERT dbo.tc_events(id, organization_id, category_id, title, venue_name, venue_address, sale_start, sale_end, start_time, end_time, status)
        VALUES ('10000000-0000-0000-0000-000000000102', '10000000-0000-0000-0000-000000000010', '10000000-0000-0000-0000-000000000030', N'Bản nháp A', N'Nhà hát A', N'Hà Nội', DATEADD(day,1,@now), DATEADD(day,20,@now), DATEADD(day,30,@now), DATEADD(day,31,@now), 'DRAFT');

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WHERE id = '10000000-0000-0000-0000-000000000103')
        INSERT dbo.tc_events(id, organization_id, category_id, commission_rule_id, title, cover_image_url, venue_name, venue_address, sale_start, sale_end, start_time, end_time, status)
        VALUES ('10000000-0000-0000-0000-000000000103', '10000000-0000-0000-0000-000000000010', '10000000-0000-0000-0000-000000000030', '10000000-0000-0000-0000-000000000031', N'Chờ duyệt A', N'https://example.test/a-pending.jpg', N'Nhà hát A', N'Hà Nội', DATEADD(day,1,@now), DATEADD(day,20,@now), DATEADD(day,30,@now), DATEADD(day,31,@now), 'PENDING_APPROVAL');

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WHERE id = '10000000-0000-0000-0000-000000000104')
        INSERT dbo.tc_events(id, organization_id, category_id, commission_rule_id, title, cover_image_url, venue_name, venue_address, sale_start, sale_end, start_time, end_time, status)
        VALUES ('10000000-0000-0000-0000-000000000104', '10000000-0000-0000-0000-000000000010', '10000000-0000-0000-0000-000000000030', '10000000-0000-0000-0000-000000000031', N'Đã hủy A', N'https://example.test/a-cancelled.jpg', N'Nhà hát A', N'Hà Nội', DATEADD(day,-10,@now), DATEADD(day,20,@now), DATEADD(day,30,@now), DATEADD(day,31,@now), 'CANCELLED');

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WHERE id = '10000000-0000-0000-0000-000000000201')
        INSERT dbo.tc_events(id, organization_id, category_id, commission_rule_id, title, cover_image_url, venue_name, venue_address, sale_start, sale_end, start_time, end_time, status)
        VALUES ('10000000-0000-0000-0000-000000000201', '10000000-0000-0000-0000-000000000020', '10000000-0000-0000-0000-000000000030', '10000000-0000-0000-0000-000000000032', N'Sự kiện B', N'https://example.test/b.jpg', N'Nhà hát B', N'Đà Nẵng', DATEADD(day,-1,@now), DATEADD(day,20,@now), DATEADD(day,30,@now), DATEADD(day,31,@now), 'PUBLISHED');

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WHERE id = '10000000-0000-0000-0000-000000000202')
        INSERT dbo.tc_events(id, organization_id, category_id, title, venue_name, venue_address, sale_start, sale_end, start_time, end_time, status)
        VALUES ('10000000-0000-0000-0000-000000000202', '10000000-0000-0000-0000-000000000020', '10000000-0000-0000-0000-000000000030', N'Bản nháp B', N'Nhà hát B', N'Đà Nẵng', DATEADD(day,1,@now), DATEADD(day,20,@now), DATEADD(day,30,@now), DATEADD(day,31,@now), 'DRAFT');

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WHERE id = '10000000-0000-0000-0000-000000000203')
        INSERT dbo.tc_events(id, organization_id, category_id, commission_rule_id, title, cover_image_url, venue_name, venue_address, sale_start, sale_end, start_time, end_time, status)
        VALUES ('10000000-0000-0000-0000-000000000203', '10000000-0000-0000-0000-000000000020', '10000000-0000-0000-0000-000000000030', '10000000-0000-0000-0000-000000000032', N'Chờ duyệt B', N'https://example.test/b-pending.jpg', N'Nhà hát B', N'Đà Nẵng', DATEADD(day,1,@now), DATEADD(day,20,@now), DATEADD(day,30,@now), DATEADD(day,31,@now), 'PENDING_APPROVAL');

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WHERE id = '10000000-0000-0000-0000-000000000204')
        INSERT dbo.tc_events(id, organization_id, category_id, commission_rule_id, title, cover_image_url, venue_name, venue_address, sale_start, sale_end, start_time, end_time, status)
        VALUES ('10000000-0000-0000-0000-000000000204', '10000000-0000-0000-0000-000000000020', '10000000-0000-0000-0000-000000000030', '10000000-0000-0000-0000-000000000032', N'Đã hủy B', N'https://example.test/b-cancelled.jpg', N'Nhà hát B', N'Đà Nẵng', DATEADD(day,-10,@now), DATEADD(day,20,@now), DATEADD(day,30,@now), DATEADD(day,31,@now), 'CANCELLED');

    UPDATE dbo.tc_events
    SET sale_start = DATEADD(day,-1,@now), sale_end = DATEADD(day,20,@now), start_time = DATEADD(day,30,@now), end_time = DATEADD(day,31,@now)
    WHERE id IN (
        '10000000-0000-0000-0000-000000000101','10000000-0000-0000-0000-000000000102',
        '10000000-0000-0000-0000-000000000103','10000000-0000-0000-0000-000000000104',
        '10000000-0000-0000-0000-000000000201','10000000-0000-0000-0000-000000000202',
        '10000000-0000-0000-0000-000000000203','10000000-0000-0000-0000-000000000204'
    );

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_zones WHERE id = '10000000-0000-0000-0000-000000000111')
        INSERT dbo.tc_zones(id, event_id, name, type, price, capacity)
        VALUES ('10000000-0000-0000-0000-000000000111', '10000000-0000-0000-0000-000000000101', N'Ghế VIP', 'SEATED', 300000, NULL);

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_zones WHERE id = '10000000-0000-0000-0000-000000000112')
        INSERT dbo.tc_zones(id, event_id, name, type, price, capacity)
        VALUES ('10000000-0000-0000-0000-000000000112', '10000000-0000-0000-0000-000000000101', N'Khu đứng', 'STANDING', 200000, 3);

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_zones WHERE id = '10000000-0000-0000-0000-000000000113')
        INSERT dbo.tc_zones(id, event_id, name, type, price, capacity)
        VALUES ('10000000-0000-0000-0000-000000000113', '10000000-0000-0000-0000-000000000101', N'Ghế miễn phí', 'SEATED', 0, NULL);

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_seats WHERE id = '10000000-0000-0000-0000-000000000121')
        INSERT dbo.tc_seats(id, zone_id, row_name, seat_number, label) VALUES
            ('10000000-0000-0000-0000-000000000121','10000000-0000-0000-0000-000000000111',N'A',1,N'A1'),
            ('10000000-0000-0000-0000-000000000122','10000000-0000-0000-0000-000000000111',N'A',2,N'A2'),
            ('10000000-0000-0000-0000-000000000123','10000000-0000-0000-0000-000000000111',N'A',3,N'A3'),
            ('10000000-0000-0000-0000-000000000124','10000000-0000-0000-0000-000000000111',N'B',1,N'B1'),
            ('10000000-0000-0000-0000-000000000125','10000000-0000-0000-0000-000000000111',N'B',2,N'B2'),
            ('10000000-0000-0000-0000-000000000126','10000000-0000-0000-0000-000000000111',N'B',3,N'B3');

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_coupons WHERE id = '10000000-0000-0000-0000-000000000131')
        INSERT dbo.tc_coupons(id, organization_id, code, discount_type, percentage_value, max_uses, valid_from, valid_to)
        VALUES ('10000000-0000-0000-0000-000000000131','10000000-0000-0000-0000-000000000010','PERCENT30','PERCENTAGE',30,100,DATEADD(day,-1,@now),DATEADD(day,60,@now));

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_coupons WHERE id = '10000000-0000-0000-0000-000000000132')
        INSERT dbo.tc_coupons(id, organization_id, code, discount_type, fixed_amount, max_uses, valid_from, valid_to)
        VALUES ('10000000-0000-0000-0000-000000000132','10000000-0000-0000-0000-000000000010','FIXED-LARGE','FIXED_AMOUNT',999999,1,DATEADD(day,-1,@now),DATEADD(day,60,@now));

    IF NOT EXISTS (SELECT 1 FROM dbo.tc_coupons WHERE id = '10000000-0000-0000-0000-000000000133')
        INSERT dbo.tc_coupons(id, organization_id, code, discount_type, percentage_value, max_uses, valid_from, valid_to)
        VALUES ('10000000-0000-0000-0000-000000000133','10000000-0000-0000-0000-000000000010','EXPIRED10','PERCENTAGE',10,10,DATEADD(day,-60,@now),DATEADD(day,-30,@now));

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
