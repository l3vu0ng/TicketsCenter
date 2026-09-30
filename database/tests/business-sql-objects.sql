SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @expected TABLE(type char(2), name sysname);
INSERT @expected(type, name) VALUES
('P', 'usp_ApproveOrganizationRequest'), ('P', 'usp_CreateTicketHold'),
('P', 'usp_ApplyOrderCoupon'), ('P', 'usp_CheckInTicket'),
('P', 'usp_CreateRefundRequest'), ('P', 'usp_CreateOrderFromHold'),
('P', 'usp_ReleaseTicketHold'), ('P', 'usp_BeginOrderPayment'),
('P', 'usp_ApplyPaymentResult'), ('P', 'usp_ReviewRefundRequest'),
('P', 'usp_ApplyRefundResult'), ('P', 'usp_PublishEvent'),
('P', 'usp_CancelEvent'), ('P', 'usp_CalculateEventSettlement'),
('P', 'usp_ConfirmEventSettlement'), ('P', 'usp_ExecutePayout'),
('P', 'usp_ProcessCancelledOrder'),
('FN', 'fn_CalculateCouponDiscount'), ('FN', 'fn_CalculateCommission'),
('IF', 'fn_GetZoneAvailability'), ('IF', 'fn_GetRefundableTickets'),
('IF', 'fn_GetOrganizationRevenue'), ('IF', 'fn_AllocateTicketPaidAmounts'),
('IF', 'fn_GetEventCheckInWindow'), ('IF', 'fn_GetCouponEligibility'),
('IF', 'fn_GetSettlementBlockers'), ('IF', 'fn_GetOrganizationCashFlow');

IF EXISTS (SELECT 1 FROM @expected e WHERE OBJECT_ID(N'dbo.' + e.name, e.type) IS NULL)
    THROW 51600, N'Missing required business procedure or function', 1;

IF (SELECT COUNT(*) FROM sys.views WHERE schema_id = SCHEMA_ID(N'dbo') AND name LIKE N'vw[_]%') < 10
    THROW 51601, N'Expected ten business views', 1;

IF (SELECT COUNT(*) FROM sys.triggers WHERE parent_class = 1 AND name LIKE N'trg[_]%') < 10
    THROW 51602, N'Expected ten business triggers', 1;

IF dbo.fn_CalculateCouponDiscount(101, 'PERCENTAGE', 30, NULL) <> 30
    THROW 51603, N'Percentage discount must floor to whole VND', 1;
IF dbo.fn_CalculateCouponDiscount(100, 'FIXED_AMOUNT', NULL, 99) <> 30
    THROW 51604, N'Fixed discount must respect the 30 percent cap', 1;
IF dbo.fn_CalculateCouponDiscount(100, 'PERCENTAGE', 31, NULL) IS NOT NULL
    THROW 51605, N'Invalid percentage must return NULL', 1;
IF dbo.fn_CalculateCommission(101, 5, 0) <> 5
    THROW 51606, N'Commission must round half up to whole VND', 1;
IF dbo.fn_CalculateCommission(10, 100, 100) <> 10
    THROW 51607, N'Commission must not exceed remaining amount', 1;

BEGIN TRANSACTION;
BEGIN TRY
    DECLARE @audit_id bigint;
    INSERT dbo.tc_audit_logs(action, aggregate_type, aggregate_id)
    VALUES ('TEST_APPEND_ONLY', 'TEST', 'ffffffff-ffff-ffff-ffff-ffffffffffff');
    SET @audit_id = SCOPE_IDENTITY();
    BEGIN TRY
        UPDATE dbo.tc_audit_logs SET action = 'ILLEGAL_UPDATE' WHERE id = @audit_id;
        THROW 51608, N'Audit update unexpectedly succeeded', 1;
    END TRY
    BEGIN CATCH
        IF ERROR_NUMBER() = 51608 THROW;
    END CATCH;
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
