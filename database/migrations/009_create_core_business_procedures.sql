SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC(N'
CREATE OR ALTER PROCEDURE dbo.usp_ApproveOrganizationRequest
    @request_id uniqueidentifier, @actor_id uniqueidentifier, @initial_commission_policy nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp01;
    BEGIN TRY
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_user_platform_roles WHERE user_id = @actor_id AND role = ''ADMIN'')
            THROW 51201, ''Administrator role required'', 1;
        DECLARE @status varchar(30), @organization_id uniqueidentifier, @applicant_id uniqueidentifier,
                @name nvarchar(200), @email nvarchar(320), @phone nvarchar(30), @description nvarchar(2000);
        SELECT @status = status, @organization_id = organization_id, @applicant_id = applicant_id,
               @name = name, @email = contact_email, @phone = contact_phone, @description = description
        FROM dbo.tc_organization_requests WITH (UPDLOCK, HOLDLOCK) WHERE id = @request_id;
        IF @status IS NULL THROW 51202, ''Organization request not found'', 1;
        IF @status = ''APPROVED'' BEGIN
            SELECT @organization_id organization_id, @status status;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @status <> ''PENDING'' THROW 51203, ''Organization request is already rejected'', 1;
        DECLARE @rate decimal(7,4) = TRY_CONVERT(decimal(7,4), JSON_VALUE(@initial_commission_policy, ''$.ratePercent'')),
                @fixed decimal(19,0) = TRY_CONVERT(decimal(19,0), JSON_VALUE(@initial_commission_policy, ''$.fixedFee'')),
                @from datetime2(3) = TRY_CONVERT(datetime2(3), JSON_VALUE(@initial_commission_policy, ''$.effectiveFrom'')),
                @to datetime2(3) = TRY_CONVERT(datetime2(3), JSON_VALUE(@initial_commission_policy, ''$.effectiveTo''));
        IF ISJSON(@initial_commission_policy) <> 1 OR @rate IS NULL OR @fixed IS NULL OR @from IS NULL OR @to IS NULL
            THROW 51204, ''Invalid initial commission policy'', 1;
        SET @organization_id = NEWID();
        INSERT dbo.tc_organizations(id, name, contact_email, contact_phone, description)
        VALUES (@organization_id, @name, @email, @phone, @description);
        INSERT dbo.tc_commission_rules(organization_id, rate_percent, fixed_fee, effective_from, effective_to)
        VALUES (@organization_id, @rate, @fixed, @from, @to);
        INSERT dbo.tc_organization_memberships(user_id, organization_id, role) VALUES (@applicant_id, @organization_id, ''MANAGER'');
        UPDATE dbo.tc_organization_requests SET reviewer_id = @actor_id, organization_id = @organization_id,
               status = ''APPROVED'', decided_at = SYSUTCDATETIME(), rejection_reason = NULL, version = version + 1
        WHERE id = @request_id;
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id)
        VALUES (@actor_id, ''ORGANIZATION_REQUEST_APPROVED'', ''ORGANIZATION_REQUEST'', @request_id);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @organization_id organization_id, CAST(''APPROVED'' AS varchar(30)) status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp01;
        THROW;
    END CATCH
END;
');

EXEC(N'
CREATE OR ALTER PROCEDURE dbo.usp_ReleaseTicketHold
    @hold_id uniqueidentifier, @actor_id uniqueidentifier = NULL, @reason varchar(30)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp07;
    BEGIN TRY
        DECLARE @event_id uniqueidentifier, @user_id uniqueidentifier, @status varchar(20), @expires datetime2(3), @now datetime2(3) = SYSUTCDATETIME();
        SELECT @event_id = h.event_id, @user_id = h.user_id FROM dbo.tc_ticket_holds h WHERE h.id = @hold_id;
        IF @event_id IS NULL THROW 51270, ''Ticket hold not found'', 1;
        SELECT 1 lock_row FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        SELECT @status = status, @expires = expires_at FROM dbo.tc_ticket_holds WITH (UPDLOCK, HOLDLOCK) WHERE id = @hold_id;
        IF @status <> ''ACTIVE'' BEGIN
            SELECT @hold_id hold_id, @status hold_status;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @reason NOT IN (''USER_CANCEL'', ''EXPIRED'', ''EVENT_CANCELLED'') THROW 51271, ''Invalid release reason'', 1;
        IF @reason = ''USER_CANCEL'' AND (@actor_id IS NULL OR @actor_id <> @user_id) THROW 51272, ''Hold owner required'', 1;
        IF @reason = ''EXPIRED'' AND @now < @expires THROW 51273, ''Hold has not expired'', 1;
        IF @reason = ''EVENT_CANCELLED'' AND NOT EXISTS (SELECT 1 FROM dbo.tc_events WHERE id = @event_id AND status = ''CANCELLED'')
            THROW 51274, ''Event is not cancelled'', 1;
        SELECT z.id FROM dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK)
        WHERE EXISTS (SELECT 1 FROM dbo.tc_ticket_hold_items hi WHERE hi.hold_id = @hold_id AND hi.zone_id = z.id) ORDER BY z.id;
        SELECT s.id FROM dbo.tc_seats s WITH (UPDLOCK, HOLDLOCK)
        WHERE EXISTS (SELECT 1 FROM dbo.tc_ticket_hold_items hi WHERE hi.hold_id = @hold_id AND hi.seat_id = s.id) ORDER BY s.id;
        UPDATE s SET status = ''AVAILABLE'', version = version + 1 FROM dbo.tc_seats s
        JOIN dbo.tc_ticket_hold_items hi ON hi.seat_id = s.id WHERE hi.hold_id = @hold_id AND s.status = ''HELD'';
        IF EXISTS (
            SELECT 1 FROM dbo.tc_zones z JOIN (
                SELECT zone_id, SUM(quantity) quantity FROM dbo.tc_ticket_hold_items
                WHERE hold_id = @hold_id AND seat_id IS NULL GROUP BY zone_id
            ) hi ON hi.zone_id = z.id WHERE z.held_quantity < hi.quantity
        ) THROW 51275, ''Held standing inventory is inconsistent'', 1;
        UPDATE z SET held_quantity = held_quantity - hi.quantity, version = version + 1
        FROM dbo.tc_zones z JOIN (
            SELECT zone_id, SUM(quantity) quantity FROM dbo.tc_ticket_hold_items WHERE hold_id = @hold_id AND seat_id IS NULL GROUP BY zone_id
        ) hi ON hi.zone_id = z.id WHERE z.held_quantity >= hi.quantity;
        UPDATE dbo.tc_coupon_redemptions SET status = ''RELEASED'', released_at = @now
        WHERE order_id IN (SELECT id FROM dbo.tc_orders WHERE hold_id = @hold_id) AND status = ''RESERVED'';
        UPDATE dbo.tc_orders SET status = CASE WHEN @reason = ''EXPIRED'' THEN ''EXPIRED'' ELSE ''CANCELLED'' END, version = version + 1
        WHERE hold_id = @hold_id AND status = ''PENDING_PAYMENT'';
        UPDATE dbo.tc_ticket_holds SET status = ''RELEASED'', version = version + 1 WHERE id = @hold_id AND status = ''ACTIVE'';
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id, detail)
        VALUES (@actor_id, ''TICKET_HOLD_RELEASED'', ''TICKET_HOLD'', @hold_id, @reason);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @hold_id hold_id, CAST(''RELEASED'' AS varchar(20)) hold_status;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp07;
        THROW;
    END CATCH
END;
');

EXEC(N'
CREATE OR ALTER PROCEDURE dbo.usp_CreateTicketHold
    @user_id uniqueidentifier, @event_id uniqueidentifier, @selections nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp02;
    BEGIN TRY
        DECLARE @now datetime2(3) = SYSUTCDATETIME(), @hold_id uniqueidentifier = NEWID(), @expires datetime2(3);
        IF ISJSON(@selections) <> 1 THROW 51220, ''Selections must be a JSON array'', 1;
        DECLARE @items TABLE(row_no int NOT NULL, zone_id uniqueidentifier NULL, seat_id uniqueidentifier NULL, quantity int NULL);
        INSERT @items SELECT CONVERT(int, [key]), TRY_CONVERT(uniqueidentifier, JSON_VALUE(value, ''$.zoneId'')),
                             TRY_CONVERT(uniqueidentifier, JSON_VALUE(value, ''$.seatId'')),
                             TRY_CONVERT(int, JSON_VALUE(value, ''$.quantity'')) FROM OPENJSON(@selections);
        IF NOT EXISTS (SELECT 1 FROM @items) OR (SELECT SUM(quantity) FROM @items) NOT BETWEEN 1 AND 8
            THROW 51221, ''A hold must contain between 1 and 8 tickets'', 1;
        IF EXISTS (SELECT 1 FROM @items WHERE zone_id IS NULL OR quantity IS NULL OR quantity <= 0)
            THROW 51222, ''Invalid hold selection'', 1;
        IF EXISTS (SELECT seat_id FROM @items WHERE seat_id IS NOT NULL GROUP BY seat_id HAVING COUNT(*) > 1)
            THROW 51223, ''Duplicate seat selection'', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_users WITH (UPDLOCK, HOLDLOCK)
                       WHERE id = @user_id AND status = ''ACTIVE'' AND email_verified_at IS NOT NULL)
            THROW 51224, ''Active verified user required'', 1;
        DECLARE @old_hold uniqueidentifier, @old_expires datetime2(3);
        SELECT @old_hold = id, @old_expires = expires_at FROM dbo.tc_ticket_holds WITH (UPDLOCK, HOLDLOCK)
        WHERE user_id = @user_id AND status = ''ACTIVE'';
        IF @old_hold IS NOT NULL AND @old_expires > @now THROW 51225, ''User already has an active hold'', 1;
        IF @old_hold IS NOT NULL EXEC dbo.usp_ReleaseTicketHold @old_hold, NULL, ''EXPIRED'';
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK)
                       WHERE id = @event_id AND status = ''PUBLISHED'' AND @now >= sale_start AND @now < sale_end)
            THROW 51226, ''Event is not on sale'', 1;
        IF EXISTS (
            SELECT 1 FROM @items i LEFT JOIN dbo.tc_zones z WITH (UPDLOCK, HOLDLOCK) ON z.id = i.zone_id
            WHERE z.id IS NULL OR z.event_id <> @event_id OR (z.type = ''SEATED'' AND (i.seat_id IS NULL OR i.quantity <> 1))
               OR (z.type = ''STANDING'' AND i.seat_id IS NOT NULL)
        ) THROW 51227, ''Selection does not match event zone type'', 1;
        IF EXISTS (
            SELECT 1 FROM @items i JOIN dbo.tc_seats s WITH (UPDLOCK, HOLDLOCK) ON s.id = i.seat_id
            WHERE i.seat_id IS NOT NULL AND (s.zone_id <> i.zone_id OR s.status <> ''AVAILABLE'')
        ) OR EXISTS (SELECT 1 FROM @items i WHERE i.seat_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM dbo.tc_seats WHERE id = i.seat_id))
            THROW 51228, ''Seat is unavailable'', 1;
        IF EXISTS (
            SELECT 1 FROM dbo.tc_zones z JOIN (SELECT zone_id, SUM(quantity) q FROM @items WHERE seat_id IS NULL GROUP BY zone_id) i ON i.zone_id = z.id
            WHERE z.capacity - z.held_quantity - z.sold_quantity < i.q
        ) THROW 51229, ''Zone capacity is unavailable'', 1;
        SET @expires = DATEADD(minute, 10, @now);
        INSERT dbo.tc_ticket_holds(id, user_id, event_id, created_at, expires_at) VALUES (@hold_id, @user_id, @event_id, @now, @expires);
        INSERT dbo.tc_ticket_hold_items(hold_id, zone_id, seat_id, quantity, unit_price)
        SELECT @hold_id, i.zone_id, i.seat_id, i.quantity, z.price FROM @items i JOIN dbo.tc_zones z ON z.id = i.zone_id;
        UPDATE s SET status = ''HELD'', version = version + 1 FROM dbo.tc_seats s JOIN @items i ON i.seat_id = s.id;
        UPDATE z SET held_quantity = held_quantity + i.q, version = version + 1 FROM dbo.tc_zones z
        JOIN (SELECT zone_id, SUM(quantity) q FROM @items WHERE seat_id IS NULL GROUP BY zone_id) i ON i.zone_id = z.id;
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @hold_id hold_id, @expires expires_at;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp02;
        THROW;
    END CATCH
END;
');

EXEC(N'
CREATE OR ALTER PROCEDURE dbo.usp_CreateOrderFromHold @hold_id uniqueidentifier, @actor_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp06;
    BEGIN TRY
        DECLARE @order_id uniqueidentifier, @event_id uniqueidentifier, @subtotal decimal(19,0), @now datetime2(3) = SYSUTCDATETIME();
        SELECT @order_id = id FROM dbo.tc_orders WITH (UPDLOCK, HOLDLOCK) WHERE hold_id = @hold_id;
        IF @order_id IS NOT NULL BEGIN
            SELECT id order_id, order_code, subtotal_amount, discount_amount, total_amount FROM dbo.tc_orders WHERE id = @order_id;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        SELECT @event_id = event_id FROM dbo.tc_ticket_holds WITH (UPDLOCK, HOLDLOCK)
        WHERE id = @hold_id AND user_id = @actor_id AND status = ''ACTIVE'' AND expires_at > @now;
        IF @event_id IS NULL THROW 51260, ''Active unexpired hold owned by actor required'', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id AND status = ''PUBLISHED'')
            THROW 51261, ''Event is unavailable'', 1;
        SELECT @subtotal = SUM(unit_price * quantity) FROM dbo.tc_ticket_hold_items WHERE hold_id = @hold_id;
        IF @subtotal IS NULL THROW 51262, ''Hold has no items'', 1;
        SET @order_id = NEWID();
        INSERT dbo.tc_orders(id, user_id, event_id, hold_id, order_code, subtotal_amount, discount_amount, total_amount)
        VALUES (@order_id, @actor_id, @event_id, @hold_id, CONCAT(''ORD-'', REPLACE(CONVERT(varchar(36), @order_id), ''-'', '''')), @subtotal, 0, @subtotal);
        INSERT dbo.tc_order_items(order_id, zone_id, seat_id, quantity, unit_price, zone_name_snapshot, seat_label_snapshot)
        SELECT @order_id, hi.zone_id, hi.seat_id, hi.quantity, hi.unit_price, z.name, s.label
        FROM dbo.tc_ticket_hold_items hi JOIN dbo.tc_zones z ON z.id = hi.zone_id LEFT JOIN dbo.tc_seats s ON s.id = hi.seat_id
        WHERE hi.hold_id = @hold_id;
        IF @started = 1 COMMIT TRANSACTION;
        SELECT id order_id, order_code, subtotal_amount, discount_amount, total_amount FROM dbo.tc_orders WHERE id = @order_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp06;
        THROW;
    END CATCH
END;
');

EXEC(N'
CREATE OR ALTER PROCEDURE dbo.usp_ApplyOrderCoupon
    @order_id uniqueidentifier, @actor_id uniqueidentifier, @coupon_code varchar(80) = NULL
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp03;
    BEGIN TRY
        DECLARE @event_id uniqueidentifier, @organization_id uniqueidentifier, @hold_id uniqueidentifier,
                @subtotal decimal(19,0), @old_coupon uniqueidentifier, @new_coupon uniqueidentifier,
                @discount decimal(19,0) = 0, @now datetime2(3) = SYSUTCDATETIME();
        SELECT @event_id = o.event_id, @hold_id = o.hold_id, @subtotal = o.subtotal_amount, @old_coupon = o.coupon_id
        FROM dbo.tc_orders o WITH (UPDLOCK, HOLDLOCK) JOIN dbo.tc_ticket_holds h ON h.id = o.hold_id
        WHERE o.id = @order_id AND o.user_id = @actor_id AND o.status = ''PENDING_PAYMENT'' AND h.status = ''ACTIVE'' AND h.expires_at > @now;
        IF @event_id IS NULL THROW 51230, ''Order is not coupon-eligible'', 1;
        SELECT @organization_id = organization_id FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id AND status = ''PUBLISHED'';
        IF @organization_id IS NULL THROW 51231, ''Event is unavailable'', 1;
        IF EXISTS (SELECT 1 FROM dbo.tc_payments WHERE order_id = @order_id AND status IN (''PENDING'', ''UNKNOWN''))
            THROW 51232, ''Coupon cannot change while payment is unresolved'', 1;
        IF @coupon_code IS NOT NULL
            SELECT @new_coupon = id FROM dbo.tc_coupons WITH (UPDLOCK, HOLDLOCK)
            WHERE organization_id = @organization_id AND code = @coupon_code;
        IF @coupon_code IS NOT NULL AND @new_coupon IS NULL THROW 51233, ''Coupon not found'', 1;
        IF @old_coupon = @new_coupon OR (@old_coupon IS NULL AND @new_coupon IS NULL) BEGIN
            SELECT subtotal_amount, discount_amount, total_amount, coupon_id FROM dbo.tc_orders WHERE id = @order_id;
            IF @started = 1 COMMIT TRANSACTION; RETURN;
        END;
        IF @new_coupon IS NOT NULL BEGIN
            IF NOT EXISTS (SELECT 1 FROM dbo.fn_GetCouponEligibility(@new_coupon, @organization_id, @subtotal, @now) WHERE is_eligible = 1)
                THROW 51234, ''Coupon is not eligible'', 1;
            SELECT @discount = dbo.fn_CalculateCouponDiscount(@subtotal, discount_type, percentage_value, fixed_amount)
            FROM dbo.tc_coupons WHERE id = @new_coupon;
            IF @discount IS NULL THROW 51235, ''Coupon discount is invalid'', 1;
        END;
        IF EXISTS (SELECT 1 FROM dbo.tc_coupon_redemptions WHERE order_id = @order_id)
            UPDATE dbo.tc_coupon_redemptions
            SET coupon_id = COALESCE(@new_coupon, coupon_id),
                status = CASE WHEN @new_coupon IS NULL THEN ''RELEASED'' ELSE ''RESERVED'' END,
                reserved_at = CASE WHEN @new_coupon IS NULL THEN reserved_at ELSE @now END,
                expires_at = CASE WHEN @new_coupon IS NULL THEN expires_at ELSE (SELECT expires_at FROM dbo.tc_ticket_holds WHERE id = @hold_id) END,
                consumed_at = NULL, released_at = CASE WHEN @new_coupon IS NULL THEN @now ELSE NULL END
            WHERE order_id = @order_id;
        ELSE IF @new_coupon IS NOT NULL
            INSERT dbo.tc_coupon_redemptions(coupon_id, order_id, expires_at)
            VALUES (@new_coupon, @order_id, (SELECT expires_at FROM dbo.tc_ticket_holds WHERE id = @hold_id));
        UPDATE dbo.tc_orders SET coupon_id = @new_coupon, discount_amount = @discount,
               total_amount = @subtotal - @discount, version = version + 1 WHERE id = @order_id;
        IF @started = 1 COMMIT TRANSACTION;
        SELECT subtotal_amount, discount_amount, total_amount, coupon_id FROM dbo.tc_orders WHERE id = @order_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp03;
        THROW;
    END CATCH
END;
');

EXEC(N'
CREATE OR ALTER PROCEDURE dbo.usp_CheckInTicket
    @event_id uniqueidentifier, @actor_id uniqueidentifier, @ticket_code varchar(100)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp04;
    BEGIN TRY
        DECLARE @organization_id uniqueidentifier, @ticket_id uniqueidentifier, @ticket_event uniqueidentifier,
                @ticket_status varchar(30), @result varchar(30), @now datetime2(3) = SYSUTCDATETIME();
        SELECT @organization_id = organization_id FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = @event_id;
        IF @organization_id IS NULL THROW 51240, ''Event not found'', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_organization_memberships WHERE user_id = @actor_id
                       AND organization_id = @organization_id AND active = 1 AND role IN (''MANAGER'', ''CHECK_IN_STAFF''))
            THROW 51241, ''Active event staff membership required'', 1;
        SELECT @ticket_id = t.id, @ticket_status = t.status, @ticket_event = o.event_id
        FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK) JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
        JOIN dbo.tc_orders o ON o.id = oi.order_id WHERE t.ticket_code = @ticket_code;
        IF @ticket_id IS NULL SET @result = ''NOT_FOUND'';
        ELSE IF @ticket_event <> @event_id SET @result = ''WRONG_EVENT'';
        ELSE IF EXISTS (SELECT 1 FROM dbo.tc_events WHERE id = @event_id AND status = ''CANCELLED'') SET @result = ''EVENT_CANCELLED'';
        ELSE IF @now < (SELECT opens_at FROM dbo.fn_GetEventCheckInWindow(@event_id, @now)) SET @result = ''TOO_EARLY'';
        ELSE IF @now >= (SELECT closes_at FROM dbo.fn_GetEventCheckInWindow(@event_id, @now)) SET @result = ''TOO_LATE'';
        ELSE IF @ticket_status = ''USED'' SET @result = ''ALREADY_USED'';
        ELSE IF @ticket_status <> ''ACTIVE'' SET @result = ''NOT_ACTIVE'';
        ELSE BEGIN UPDATE dbo.tc_tickets SET status = ''USED'', version = version + 1 WHERE id = @ticket_id; SET @result = ''SUCCESS''; END;
        INSERT dbo.tc_check_ins(event_id, ticket_id, actor_id, result, scanned_at)
        VALUES (@event_id, CASE WHEN @result = ''NOT_FOUND'' THEN NULL ELSE @ticket_id END, @actor_id, @result, @now);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @result result, @now scanned_at, @ticket_id ticket_id;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp04;
        THROW;
    END CATCH
END;
');

EXEC(N'
CREATE OR ALTER PROCEDURE dbo.usp_CreateRefundRequest
    @order_id uniqueidentifier, @actor_id uniqueidentifier, @ticket_ids nvarchar(max), @reason nvarchar(2000)
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp05;
    BEGIN TRY
        IF ISJSON(@ticket_ids) <> 1 OR NULLIF(LTRIM(RTRIM(@reason)), N'''') IS NULL THROW 51250, ''Ticket list and reason are required'', 1;
        DECLARE @tickets TABLE(id uniqueidentifier NULL PRIMARY KEY);
        INSERT @tickets SELECT TRY_CONVERT(uniqueidentifier, value) FROM OPENJSON(@ticket_ids);
        IF NOT EXISTS (SELECT 1 FROM @tickets) OR EXISTS (SELECT 1 FROM @tickets WHERE id IS NULL) THROW 51251, ''Invalid ticket list'', 1;
        IF (SELECT COUNT(*) FROM OPENJSON(@ticket_ids)) <> (SELECT COUNT(*) FROM @tickets) THROW 51252, ''Duplicate ticket id'', 1;
        DECLARE @event_id uniqueidentifier, @start datetime2(3), @request_id uniqueidentifier = NEWID(), @amount decimal(19,0);
        SELECT @event_id = o.event_id, @start = e.start_time FROM dbo.tc_orders o WITH (UPDLOCK, HOLDLOCK)
        JOIN dbo.tc_events e WITH (UPDLOCK, HOLDLOCK) ON e.id = o.event_id WHERE o.id = @order_id AND o.user_id = @actor_id AND o.status = ''PAID'';
        IF @event_id IS NULL THROW 51253, ''Paid order owned by actor required'', 1;
        IF SYSUTCDATETIME() >= @start THROW 51254, ''Refund requests close at event start'', 1;
        SELECT t.id FROM dbo.tc_tickets t WITH (UPDLOCK, HOLDLOCK) JOIN @tickets x ON x.id = t.id ORDER BY t.id;
        IF EXISTS (
            SELECT 1 FROM @tickets x LEFT JOIN dbo.tc_tickets t ON t.id = x.id
            LEFT JOIN dbo.tc_order_items oi ON oi.id = t.order_item_id
            WHERE t.id IS NULL OR oi.order_id <> @order_id OR t.status <> ''ACTIVE''
               OR EXISTS (SELECT 1 FROM dbo.tc_refund_request_tickets rt WHERE rt.ticket_id = x.id AND rt.is_open = 1)
        ) THROW 51255, ''One or more tickets are not refundable'', 1;
        INSERT dbo.tc_refund_requests(id, order_id, requester_id, reason, reason_type)
        VALUES (@request_id, @order_id, @actor_id, @reason, ''CUSTOMER_REQUEST'');
        INSERT dbo.tc_refund_request_tickets(refund_request_id, ticket_id) SELECT @request_id, id FROM @tickets;
        UPDATE t SET status = ''REFUND_PENDING'', version = version + 1 FROM dbo.tc_tickets t JOIN @tickets x ON x.id = t.id;
        SELECT @amount = SUM(t.paid_amount) FROM dbo.tc_tickets t JOIN @tickets x ON x.id = t.id;
        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id)
        VALUES (@actor_id, ''REFUND_REQUEST_CREATED'', ''REFUND_REQUEST'', @request_id);
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @request_id request_id, @amount requested_amount;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp05;
        THROW;
    END CATCH
END;
');

EXEC(N'
CREATE OR ALTER PROCEDURE dbo.usp_BeginOrderPayment @order_id uniqueidentifier, @actor_id uniqueidentifier
AS
BEGIN
    SET NOCOUNT ON; SET XACT_ABORT ON;
    DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
    IF @started = 1 BEGIN TRANSACTION; ELSE SAVE TRANSACTION tc_sp08;
    BEGIN TRY
        DECLARE @event_id uniqueidentifier, @hold_id uniqueidentifier, @amount decimal(19,0), @payment_id uniqueidentifier,
                @txn_ref varchar(100), @now datetime2(3) = SYSUTCDATETIME();
        SELECT @event_id = o.event_id, @hold_id = o.hold_id, @amount = o.total_amount
        FROM dbo.tc_orders o WITH (UPDLOCK, HOLDLOCK) JOIN dbo.tc_ticket_holds h WITH (UPDLOCK, HOLDLOCK) ON h.id = o.hold_id
        WHERE o.id = @order_id AND o.user_id = @actor_id AND o.status = ''PENDING_PAYMENT'' AND h.status = ''ACTIVE'' AND h.expires_at > @now;
        IF @event_id IS NULL THROW 51280, ''Order cannot begin payment'', 1;
        IF NOT EXISTS (SELECT 1 FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK)
                       WHERE id = @event_id AND status = ''PUBLISHED'' AND @now < sale_end)
            THROW 51281, ''Event is unavailable for payment'', 1;
        IF @amount = 0 THROW 51282, ''Zero-value order must use the free completion flow'', 1;
        SELECT TOP (1) @payment_id = id, @txn_ref = txn_ref FROM dbo.tc_payments WITH (UPDLOCK, HOLDLOCK)
        WHERE order_id = @order_id AND status IN (''PENDING'', ''UNKNOWN'') ORDER BY created_at DESC;
        IF @payment_id IS NULL BEGIN
            SET @payment_id = NEWID(); SET @txn_ref = CONCAT(''PAY-'', REPLACE(CONVERT(varchar(36), @payment_id), ''-'', ''''));
            INSERT dbo.tc_payments(id, order_id, txn_ref, amount) VALUES (@payment_id, @order_id, @txn_ref, @amount);
        END;
        IF @started = 1 COMMIT TRANSACTION;
        SELECT @payment_id payment_id, @txn_ref txn_ref, @amount amount, CAST(''VND'' AS char(3)) currency;
    END TRY BEGIN CATCH
        IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
        ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_sp08;
        THROW;
    END CATCH
END;
');
