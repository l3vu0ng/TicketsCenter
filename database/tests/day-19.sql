SET NOCOUNT ON;
SET XACT_ABORT ON;

-- Recover cleanly if a previous assertion aborted before teardown.
IF DATABASE_PRINCIPAL_ID('tc_day19_buyer') IS NOT NULL ALTER ROLE tc_buyer DROP MEMBER tc_day19_buyer;
IF DATABASE_PRINCIPAL_ID('tc_day19_manager') IS NOT NULL ALTER ROLE tc_manager DROP MEMBER tc_day19_manager;
IF DATABASE_PRINCIPAL_ID('tc_day19_checkin') IS NOT NULL ALTER ROLE tc_checkin DROP MEMBER tc_day19_checkin;
IF DATABASE_PRINCIPAL_ID('tc_day19_admin') IS NOT NULL ALTER ROLE tc_platform_admin DROP MEMBER tc_day19_admin;
IF DATABASE_PRINCIPAL_ID('tc_day19_worker') IS NOT NULL ALTER ROLE tc_worker DROP MEMBER tc_day19_worker;
IF DATABASE_PRINCIPAL_ID('tc_day19_buyer') IS NOT NULL DROP USER tc_day19_buyer;
IF DATABASE_PRINCIPAL_ID('tc_day19_manager') IS NOT NULL DROP USER tc_day19_manager;
IF DATABASE_PRINCIPAL_ID('tc_day19_checkin') IS NOT NULL DROP USER tc_day19_checkin;
IF DATABASE_PRINCIPAL_ID('tc_day19_admin') IS NOT NULL DROP USER tc_day19_admin;
IF DATABASE_PRINCIPAL_ID('tc_day19_worker') IS NOT NULL DROP USER tc_day19_worker;
IF DATABASE_PRINCIPAL_ID('tc_day19_revoke') IS NOT NULL DROP USER tc_day19_revoke;

-- These are contained test users: their effective permissions are exactly the
-- production role permissions, without needing a committed login credential.
CREATE USER tc_day19_buyer WITHOUT LOGIN;
CREATE USER tc_day19_manager WITHOUT LOGIN;
CREATE USER tc_day19_checkin WITHOUT LOGIN;
CREATE USER tc_day19_admin WITHOUT LOGIN;
CREATE USER tc_day19_worker WITHOUT LOGIN;
ALTER ROLE tc_buyer ADD MEMBER tc_day19_buyer;
ALTER ROLE tc_manager ADD MEMBER tc_day19_manager;
ALTER ROLE tc_checkin ADD MEMBER tc_day19_checkin;
ALTER ROLE tc_platform_admin ADD MEMBER tc_day19_admin;
ALTER ROLE tc_worker ADD MEMBER tc_day19_worker;

BEGIN TRY
    EXECUTE AS USER = 'tc_day19_buyer';
    IF HAS_PERMS_BY_NAME('dbo.vw_PublicEvents', 'OBJECT', 'SELECT') <> 1
        THROW 52300, 'buyer lost public-event read', 1;
    IF HAS_PERMS_BY_NAME('dbo.usp_ProcessCancelledOrder', 'OBJECT', 'EXECUTE') <> 0
        THROW 52301, 'buyer may process cancelled orders', 1;
    IF HAS_PERMS_BY_NAME('dbo.tc_payments', 'OBJECT', 'SELECT') <> 0
        THROW 52302, 'buyer has direct payment-table read', 1;
    REVERT;

    EXECUTE AS USER = 'tc_day19_manager';
    IF HAS_PERMS_BY_NAME('dbo.vw_CheckInHistory', 'OBJECT', 'SELECT') <> 1
        THROW 52303, 'manager lost check-in history', 1;
    IF HAS_PERMS_BY_NAME('dbo.usp_CancelEvent', 'OBJECT', 'EXECUTE') <> 0
        THROW 52304, 'manager may cancel events as admin', 1;
    REVERT;

    EXECUTE AS USER = 'tc_day19_checkin';
    IF HAS_PERMS_BY_NAME('dbo.vw_CheckInHistory', 'OBJECT', 'SELECT') <> 1
        THROW 52305, 'check-in lost operational history', 1;
    IF HAS_PERMS_BY_NAME('dbo.vw_TicketDetails', 'OBJECT', 'SELECT') <> 0
        THROW 52306, 'check-in may read V06 financial ticket details', 1;
    IF HAS_PERMS_BY_NAME('dbo.tc_payments', 'OBJECT', 'SELECT') <> 0
        THROW 52307, 'check-in may read payment base table', 1;
    IF HAS_PERMS_BY_NAME('dbo.fn_CalculateCommission', 'OBJECT', 'EXECUTE') <> 0
        THROW 52308, 'check-in may read financial UDF', 1;
    REVERT;

    EXECUTE AS USER = 'tc_day19_admin';
    IF HAS_PERMS_BY_NAME('dbo.vw_OrderFinancialSummary', 'OBJECT', 'SELECT') <> 1
        THROW 52309, 'admin lost financial report read', 1;
    IF HAS_PERMS_BY_NAME('dbo.usp_DecideRefundRequest', 'OBJECT', 'EXECUTE') <> 1
        THROW 52310, 'admin lost refund decision procedure', 1;
    REVERT;

    EXECUTE AS USER = 'tc_day19_worker';
    IF HAS_PERMS_BY_NAME('dbo.usp_ProcessCancelledOrder', 'OBJECT', 'EXECUTE') <> 1
        THROW 52311, 'worker lost cancelled-order procedure', 1;
    IF HAS_PERMS_BY_NAME('dbo.usp_CancelEvent', 'OBJECT', 'EXECUTE') <> 0
        THROW 52312, 'worker may cancel arbitrary events', 1;
    REVERT;

    -- A temporary grant must disappear after REVOKE when it has no other source.
    CREATE USER tc_day19_revoke WITHOUT LOGIN;
    GRANT SELECT ON dbo.vw_PublicEvents TO tc_day19_revoke;
    EXECUTE AS USER = 'tc_day19_revoke';
    IF HAS_PERMS_BY_NAME('dbo.vw_PublicEvents', 'OBJECT', 'SELECT') <> 1
        THROW 52313, 'temporary grant did not take effect', 1;
    REVERT;
    REVOKE SELECT ON dbo.vw_PublicEvents FROM tc_day19_revoke;
    EXECUTE AS USER = 'tc_day19_revoke';
    IF HAS_PERMS_BY_NAME('dbo.vw_PublicEvents', 'OBJECT', 'SELECT') <> 0
        THROW 52314, 'temporary grant survived revoke', 1;
    REVERT;
    DROP USER tc_day19_revoke;

    IF EXISTS (
        SELECT 1 FROM sys.database_role_members rm
        JOIN sys.database_principals role_member ON role_member.principal_id = rm.member_principal_id
        JOIN sys.database_principals role_name ON role_name.principal_id = rm.role_principal_id
        WHERE role_name.name = 'db_owner'
          AND role_member.name IN ('tc_buyer','tc_manager','tc_checkin','tc_platform_admin','tc_auth','tc_worker'))
        THROW 52315, 'runtime role is db_owner', 1;
END TRY
BEGIN CATCH
    IF USER_NAME() <> ORIGINAL_LOGIN() AND USER_NAME() LIKE 'tc_day19_%' REVERT;
    THROW;
END CATCH;

ALTER ROLE tc_buyer DROP MEMBER tc_day19_buyer;
ALTER ROLE tc_manager DROP MEMBER tc_day19_manager;
ALTER ROLE tc_checkin DROP MEMBER tc_day19_checkin;
ALTER ROLE tc_platform_admin DROP MEMBER tc_day19_admin;
ALTER ROLE tc_worker DROP MEMBER tc_day19_worker;
DROP USER tc_day19_buyer;
DROP USER tc_day19_manager;
DROP USER tc_day19_checkin;
DROP USER tc_day19_admin;
DROP USER tc_day19_worker;
