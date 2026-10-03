SET NOCOUNT ON;
SET XACT_ABORT OFF;
BEGIN TRANSACTION;
BEGIN TRY
    IF dbo.fn_CalculateCommission(300000, 10, 1000) <> 31000
        THROW 51900, 'F02 expected 31000', 1;
    IF dbo.fn_CalculateCommission(0, 10, 1000) <> 0
        THROW 51901, 'F02 expected zero', 1;
    IF dbo.fn_CalculateCommission(300000, 0, 400000) <> 300000
        THROW 51902, 'F02 did not cap fixed fee', 1;
    IF dbo.fn_CalculateCommission(NULL, 10, 1000) IS NOT NULL
        THROW 51903, 'F02 accepted NULL', 1;

    DECLARE @now datetime2(3) = SYSUTCDATETIME(),
            @admin uniqueidentifier = NEWID(), @buyer uniqueidentifier = NEWID(),
            @org uniqueidentifier = NEWID(), @category uniqueidentifier = NEWID(),
            @rule uniqueidentifier = NEWID(), @newRule uniqueidentifier = NEWID(),
            @event uniqueidentifier = NEWID(), @zone uniqueidentifier = NEWID(),
            @hold1 uniqueidentifier = NEWID(), @hold2 uniqueidentifier = NEWID(),
            @order1 uniqueidentifier = NEWID(), @order2 uniqueidentifier = NEWID(),
            @item1 uniqueidentifier = NEWID(), @item2 uniqueidentifier = NEWID(),
            @payment1 uniqueidentifier = NEWID(), @payment2 uniqueidentifier = NEWID(),
            @compPayment uniqueidentifier = NEWID(), @unknownPayment uniqueidentifier = NEWID(),
            @ticket1 uniqueidentifier = NEWID(), @ticket2 uniqueidentifier = NEWID(), @ticket3 uniqueidentifier = NEWID(),
            @request1 uniqueidentifier = NEWID(), @request2 uniqueidentifier = NEWID(),
            @refund1 uniqueidentifier = NEWID(), @refund2 uniqueidentifier = NEWID(),
            @compFailed uniqueidentifier = NEWID(), @compSucceeded uniqueidentifier = NEWID();

    INSERT dbo.tc_users(id,email,normalized_email,password_hash,full_name,email_verified_at) VALUES
        (@admin,CONCAT('day17-admin-',@admin,'@example.test'),CONCAT('day17-admin-',@admin,'@example.test'),'test','Day 17 Admin',@now),
        (@buyer,CONCAT('day17-buyer-',@buyer,'@example.test'),CONCAT('day17-buyer-',@buyer,'@example.test'),'test','Day 17 Buyer',@now);
    INSERT dbo.tc_user_platform_roles(user_id,role) VALUES(@admin,'ADMIN');
    INSERT dbo.tc_organizations(id,name,contact_email) VALUES(@org,'Day 17 Org','day17@example.test');
    INSERT dbo.tc_event_categories(id,name,slug) VALUES(@category,'Day 17',CONCAT('day-17-',@category));
    INSERT dbo.tc_commission_rules(id,organization_id,rate_percent,fixed_fee,effective_from,effective_to) VALUES
        (@rule,@org,10,1000,DATEADD(day,-30,@now),DATEADD(day,30,@now)),
        (@newRule,@org,25,0,DATEADD(day,-1,@now),DATEADD(day,60,@now));
    INSERT dbo.tc_events(id,organization_id,category_id,commission_rule_id,title,venue_name,venue_address,
                         sale_start,sale_end,start_time,end_time,status)
    VALUES(@event,@org,@category,@rule,'Settled Event','Venue','Address',DATEADD(day,-5,@now),
           DATEADD(day,-4,@now),DATEADD(day,-2,@now),DATEADD(day,-1,@now),'DRAFT');
    INSERT dbo.tc_zones(id,event_id,name,type,price,capacity,sold_quantity)
    VALUES(@zone,@event,'Standing','STANDING',100000,10,3);
    UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id=@event;

    INSERT dbo.tc_ticket_holds(id,user_id,event_id,status,expires_at) VALUES
        (@hold1,@buyer,@event,'CONSUMED',DATEADD(minute,10,@now)),
        (@hold2,@buyer,@event,'CONSUMED',DATEADD(minute,10,@now));
    INSERT dbo.tc_orders(id,user_id,event_id,hold_id,order_code,subtotal_amount,discount_amount,total_amount,status,paid_at) VALUES
        (@order1,@buyer,@event,@hold1,CONCAT('D17-',@order1),300000,0,300000,'PAID',DATEADD(day,-3,@now)),
        (@order2,@buyer,@event,@hold2,CONCAT('D17-',@order2),100000,0,100000,'PAID',DATEADD(day,-3,@now));
    INSERT dbo.tc_order_items(id,order_id,zone_id,quantity,unit_price,zone_name_snapshot) VALUES
        (@item1,@order1,@zone,2,150000,'Standing'),
        (@item2,@order2,@zone,1,100000,'Standing');
    INSERT dbo.tc_payments(id,order_id,txn_ref,amount,status,captured_at) VALUES
        (@payment1,@order1,CONCAT('D17-',@payment1),300000,'CAPTURED',DATEADD(day,-3,@now)),
        (@payment2,@order2,CONCAT('D17-',@payment2),100000,'CAPTURED',DATEADD(day,-3,@now)),
        (@compPayment,@order1,CONCAT('D17-',@compPayment),300000,'CAPTURED',DATEADD(day,-3,@now));
    INSERT dbo.tc_tickets(id,order_item_id,ticket_code,qr_secret_hash,paid_amount,status) VALUES
        (@ticket1,@item1,CONCAT('D17-',@ticket1),0x01,200000,'ACTIVE'),
        (@ticket2,@item1,CONCAT('D17-',@ticket2),0x02,100000,'REFUNDED'),
        (@ticket3,@item2,CONCAT('D17-',@ticket3),0x03,100000,'REFUNDED');
    INSERT dbo.tc_refund_requests(id,order_id,requester_id,reviewer_id,reason,reason_type,status,decided_at) VALUES
        (@request1,@order1,@buyer,@admin,'Partial','CUSTOMER_REQUEST','COMPLETED',DATEADD(day,-2,@now)),
        (@request2,@order2,@buyer,@admin,'Full','CUSTOMER_REQUEST','COMPLETED',DATEADD(day,-2,@now));
    INSERT dbo.tc_refund_request_tickets(refund_request_id,ticket_id,is_open) VALUES
        (@request1,@ticket2,0),(@request2,@ticket3,0);
    INSERT dbo.tc_refunds(id,refund_request_id,payment_id,purpose,amount,status,provider_reference,processed_at) VALUES
        (@refund1,@request1,@payment1,'CUSTOMER_REFUND',100000,'SUCCEEDED','R1',DATEADD(day,-2,@now)),
        (@refund2,@request2,@payment2,'CUSTOMER_REFUND',100000,'SUCCEEDED','R2',DATEADD(day,-2,@now)),
        (@compFailed,NULL,@compPayment,'PAYMENT_COMPENSATION',300000,'FAILED','CF',DATEADD(day,-2,@now)),
        (@compSucceeded,NULL,@compPayment,'PAYMENT_COMPENSATION',300000,'SUCCEEDED','CS',DATEADD(day,-2,@now));

    IF (SELECT ticket_gross_amount FROM dbo.vw_OrderFinancialSummary WHERE order_id=@order1) <> 300000
        THROW 51904, 'V03 multiplied or included compensation capture', 1;
    IF (SELECT remaining_ticket_amount FROM dbo.vw_OrderFinancialSummary WHERE order_id=@order1) <> 200000
        THROW 51905, 'V03 partial refund remaining is wrong', 1;
    IF (SELECT remaining_ticket_amount FROM dbo.vw_OrderFinancialSummary WHERE order_id=@order2) <> 0
        THROW 51906, 'V03 full refund remaining is wrong', 1;
    IF EXISTS(SELECT 1 FROM dbo.fn_GetSettlementBlockers(@event,@now))
        THROW 51907, 'F09 kept resolved failed compensation as blocker', 1;

    INSERT dbo.tc_payments(id,order_id,txn_ref,amount,status)
    VALUES(@unknownPayment,@order1,CONCAT('D17-',@unknownPayment),300000,'UNKNOWN');
    IF NOT EXISTS(SELECT 1 FROM dbo.fn_GetSettlementBlockers(@event,@now)
                  WHERE blocker_type='PAYMENT_UNRESOLVED' AND blocker_id=@unknownPayment)
        THROW 51908, 'F09 ignored UNKNOWN payment', 1;
    DELETE FROM dbo.tc_payments WHERE id=@unknownPayment;

    DECLARE @futureEvent uniqueidentifier=NEWID();
    INSERT dbo.tc_events(id,organization_id,category_id,commission_rule_id,title,venue_name,venue_address,
                         sale_start,sale_end,start_time,end_time,status)
    VALUES(@futureEvent,@org,@category,@rule,'Cancelled Future','Venue','Address',DATEADD(day,-2,@now),
           DATEADD(hour,-1,@now),DATEADD(hour,1,@now),DATEADD(hour,2,@now),'CANCELLED');
    IF NOT EXISTS(SELECT 1 FROM dbo.fn_GetSettlementBlockers(@futureEvent,@now) WHERE blocker_type='EVENT_NOT_ENDED')
        THROW 51909, 'F09 let cancelled event settle before end', 1;
    DECLARE @empty TABLE(settlement_id uniqueidentifier,status varchar(20),gross_revenue decimal(19,0),
                         total_refund decimal(19,0),total_commission decimal(19,0),net_payable decimal(19,0));
    INSERT @empty EXEC dbo.usp_RecalculateSettlement @futureEvent,@admin;
    IF NOT EXISTS(SELECT 1 FROM @empty WHERE settlement_id IS NULL AND status='EMPTY')
       OR EXISTS(SELECT 1 FROM dbo.tc_settlements WHERE event_id=@futureEvent)
        THROW 51924, 'SP14 created an empty settlement', 1;

    DECLARE @zeroEvent uniqueidentifier=NEWID(), @zeroZone uniqueidentifier=NEWID(),
            @zeroHold uniqueidentifier=NEWID(), @zeroOrder uniqueidentifier=NEWID(),
            @zeroItem uniqueidentifier=NEWID(), @zeroPayment uniqueidentifier=NEWID(),
            @zeroTicket uniqueidentifier=NEWID(), @zeroRequest uniqueidentifier=NEWID(),
            @zeroRefund uniqueidentifier=NEWID();
    INSERT dbo.tc_events(id,organization_id,category_id,commission_rule_id,title,venue_name,venue_address,
                         sale_start,sale_end,start_time,end_time,status)
    VALUES(@zeroEvent,@org,@category,@rule,'Zero Net','Venue','Address',DATEADD(day,-5,@now),
           DATEADD(day,-4,@now),DATEADD(day,-2,@now),DATEADD(day,-1,@now),'DRAFT');
    INSERT dbo.tc_zones(id,event_id,name,type,price,capacity,sold_quantity)
    VALUES(@zeroZone,@zeroEvent,'Standing','STANDING',100000,1,1);
    UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id=@zeroEvent;
    INSERT dbo.tc_ticket_holds(id,user_id,event_id,status,expires_at)
    VALUES(@zeroHold,@buyer,@zeroEvent,'CONSUMED',DATEADD(minute,10,@now));
    INSERT dbo.tc_orders(id,user_id,event_id,hold_id,order_code,subtotal_amount,discount_amount,total_amount,status,paid_at)
    VALUES(@zeroOrder,@buyer,@zeroEvent,@zeroHold,CONCAT('D17-',@zeroOrder),100000,0,100000,'PAID',DATEADD(day,-3,@now));
    INSERT dbo.tc_order_items(id,order_id,zone_id,quantity,unit_price,zone_name_snapshot)
    VALUES(@zeroItem,@zeroOrder,@zeroZone,1,100000,'Standing');
    INSERT dbo.tc_payments(id,order_id,txn_ref,amount,status,captured_at)
    VALUES(@zeroPayment,@zeroOrder,CONCAT('D17-',@zeroPayment),100000,'CAPTURED',DATEADD(day,-3,@now));
    INSERT dbo.tc_tickets(id,order_item_id,ticket_code,qr_secret_hash,paid_amount,status)
    VALUES(@zeroTicket,@zeroItem,CONCAT('D17-',@zeroTicket),0x04,100000,'REFUNDED');
    INSERT dbo.tc_refund_requests(id,order_id,requester_id,reviewer_id,reason,reason_type,status,decided_at)
    VALUES(@zeroRequest,@zeroOrder,@buyer,@admin,'Full','CUSTOMER_REQUEST','COMPLETED',DATEADD(day,-2,@now));
    INSERT dbo.tc_refund_request_tickets(refund_request_id,ticket_id,is_open)
    VALUES(@zeroRequest,@zeroTicket,0);
    INSERT dbo.tc_refunds(id,refund_request_id,payment_id,purpose,amount,status,provider_reference,processed_at)
    VALUES(@zeroRefund,@zeroRequest,@zeroPayment,'CUSTOMER_REFUND',100000,'SUCCEEDED','R0',DATEADD(day,-2,@now));
    DECLARE @zeroResult TABLE(settlement_id uniqueidentifier,status varchar(20),gross_revenue decimal(19,0),
                              total_refund decimal(19,0),total_commission decimal(19,0),net_payable decimal(19,0));
    INSERT @zeroResult EXEC dbo.usp_RecalculateSettlement @zeroEvent,@admin;
    DECLARE @zeroSettlement uniqueidentifier=(SELECT settlement_id FROM @zeroResult);
    EXEC dbo.usp_ConfirmSettlement @zeroSettlement,@admin;
    IF (SELECT status FROM dbo.tc_settlements WHERE id=@zeroSettlement)<>'PAID'
       OR EXISTS(SELECT 1 FROM dbo.tc_payouts WHERE settlement_id=@zeroSettlement)
        THROW 51925, 'SP15 did not complete zero-net settlement without payout', 1;

    DECLARE @recalculated TABLE(settlement_id uniqueidentifier,status varchar(20),gross_revenue decimal(19,0),
                                total_refund decimal(19,0),total_commission decimal(19,0),net_payable decimal(19,0));
    INSERT @recalculated EXEC dbo.usp_RecalculateSettlement @event,@admin;
    DECLARE @settlement uniqueidentifier=(SELECT settlement_id FROM @recalculated);
    IF NOT EXISTS(SELECT 1 FROM @recalculated WHERE gross_revenue=400000 AND total_refund=200000
                  AND total_commission=21000 AND net_payable=179000)
        THROW 51910, 'SP14 snapshot totals are wrong', 1;
    IF (SELECT COUNT(*) FROM dbo.tc_settlement_items WHERE settlement_id=@settlement)<>2
        THROW 51911, 'SP14 did not create one item per eligible order', 1;
    EXEC dbo.usp_RecalculateSettlement @event,@admin;
    IF (SELECT COUNT(*) FROM dbo.tc_settlements WHERE event_id=@event)<>1
        THROW 51912, 'SP14 duplicated settlement', 1;

    EXEC dbo.usp_ConfirmSettlement @settlement,@admin;
    IF (SELECT status FROM dbo.tc_settlements WHERE id=@settlement)<>'CONFIRMED'
        THROW 51913, 'SP15 did not confirm', 1;
    EXEC dbo.usp_ConfirmSettlement @settlement,@admin;
    IF (SELECT net_payable FROM dbo.tc_settlements WHERE id=@settlement)<>179000
        THROW 51914, 'SP15 repeat changed snapshot', 1;

    BEGIN TRY
        UPDATE dbo.tc_settlement_items SET net_amount=net_amount-1,commission_amount=commission_amount+1
        WHERE settlement_id=@settlement;
        THROW 51915, 'TR04 allowed multirow confirmed item edit', 1;
    END TRY BEGIN CATCH
        IF ERROR_NUMBER()=51915 THROW;
    END CATCH;
    BEGIN TRY
        UPDATE dbo.tc_settlements SET net_payable=net_payable-1,total_commission=total_commission+1 WHERE id=@settlement;
        THROW 51916, 'TR09 allowed confirmed header edit', 1;
    END TRY BEGIN CATCH
        IF ERROR_NUMBER()=51916 THROW;
    END CATCH;
    DECLARE @draftSettlement uniqueidentifier=NEWID();
    INSERT dbo.tc_settlements(id,event_id,status) VALUES(@draftSettlement,@futureEvent,'DRAFT');
    BEGIN TRY
        UPDATE dbo.tc_settlement_items SET settlement_id=@draftSettlement
        WHERE id=(SELECT TOP(1) id FROM dbo.tc_settlement_items WHERE settlement_id=@settlement ORDER BY id);
        THROW 51926, 'TR04 allowed item move from confirmed parent', 1;
    END TRY BEGIN CATCH
        IF ERROR_NUMBER()=51926 THROW;
    END CATCH;

    DECLARE @failedPayout uniqueidentifier=NEWID(), @paidPayout uniqueidentifier=NEWID();
    EXEC dbo.usp_RecordPayout @settlement,@failedPayout,@admin,100000,'SIM-FAILED','PENDING';
    IF (SELECT pending_amount FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id=@settlement)<>100000
        THROW 51917, 'V08 reported pending balance incorrectly', 1;
    EXEC dbo.usp_RecordPayout @settlement,@failedPayout,@admin,100000,'SIM-FAILED','FAILED';
    IF (SELECT available_amount FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id=@settlement)<>179000
        THROW 51918, 'failed payout did not release available balance', 1;
    EXEC dbo.usp_RecordPayout @settlement,@paidPayout,@admin,179000,'SIM-PAID','SUCCEEDED';
    EXEC dbo.usp_RecordPayout @settlement,@paidPayout,@admin,179000,'SIM-PAID','SUCCEEDED';
    IF (SELECT status FROM dbo.tc_settlements WHERE id=@settlement)<>'PAID'
        THROW 51919, 'SP16 did not mark exactly paid settlement', 1;
    IF (SELECT paid_amount FROM dbo.vw_SettlementPayoutBalance WHERE settlement_id=@settlement)<>179000
        THROW 51920, 'SP16 replay duplicated payout', 1;
    DECLARE @overPayout uniqueidentifier=NEWID();
    BEGIN TRY
        EXEC dbo.usp_RecordPayout @settlement,@overPayout,@admin,1,'SIM-OVER','SUCCEEDED';
        THROW 51921, 'SP16 allowed payout after paid', 1;
    END TRY BEGIN CATCH
        IF ERROR_NUMBER()=51921 THROW;
    END CATCH;

    IF (SELECT SUM(net_amount) FROM dbo.tc_settlement_items WHERE settlement_id=@settlement)
       <> (SELECT net_payable FROM dbo.tc_settlements WHERE id=@settlement)
        THROW 51922, 'Settlement item/header totals differ', 1;
    IF (SELECT SUM(amount) FROM dbo.tc_payouts WHERE settlement_id=@settlement AND status='SUCCEEDED')
       > (SELECT net_payable FROM dbo.tc_settlements WHERE id=@settlement)
        THROW 51923, 'Successful payouts exceed net payable', 1;

    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
