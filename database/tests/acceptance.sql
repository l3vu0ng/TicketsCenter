SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @indexes TABLE(name sysname PRIMARY KEY);
INSERT @indexes VALUES
(N'IX_Event_Status_StartTime'),(N'IX_Seat_Zone_Status'),(N'IX_Order_User_CreatedAt'),
(N'IX_Payment_Status_CreatedAt'),(N'IX_CheckIn_Event_ScannedAt'),(N'IX_TicketHold_Status_ExpiresAt'),
(N'IX_Order_Event_Status_PaidAt'),(N'IX_Refund_Status_CreatedAt'),(N'IX_CouponRedemption_Coupon_Status'),
(N'IX_RefundRequest_Status_RequestedAt'),(N'IX_Ticket_OrderItem_Status'),(N'IX_Payout_Settlement_Status'),
(N'IX_AuditLog_Aggregate_CreatedAt'),(N'IX_CommissionRule_Organization_EffectiveFrom'),
(N'IX_Event_Organization_Status_StartTime');

IF (SELECT COUNT(*) FROM @indexes) <> 15 THROW 52500, 'IX acceptance list is incomplete', 1;
IF EXISTS (SELECT 1 FROM @indexes expected WHERE NOT EXISTS (SELECT 1 FROM sys.indexes actual WHERE actual.name = expected.name))
    THROW 52501, 'Required performance index is missing', 1;
IF EXISTS (
    SELECT 1 FROM sys.database_role_members rm
    JOIN sys.database_principals member_role ON member_role.principal_id = rm.member_principal_id
    JOIN sys.database_principals owner_role ON owner_role.principal_id = rm.role_principal_id
    WHERE owner_role.name = N'db_owner'
      AND member_role.name IN (N'tc_buyer', N'tc_manager', N'tc_checkin', N'tc_platform_admin', N'tc_auth', N'tc_worker'))
    THROW 52502, 'Runtime role is db_owner', 1;
