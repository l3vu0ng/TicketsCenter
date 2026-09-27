SET NOCOUNT ON;

IF EXISTS (
    SELECT required.name
    FROM (VALUES (N'tc_buyer'), (N'tc_manager'), (N'tc_checkin'),
                 (N'tc_platform_admin'), (N'tc_auth'), (N'tc_worker')) required(name)
    WHERE DATABASE_PRINCIPAL_ID(required.name) IS NULL
)
    THROW 51000, N'D03: missing runtime database role', 1;

IF HAS_PERMS_BY_NAME(N'dbo.tc_payments', N'OBJECT', N'UPDATE') = 1
    THROW 51001, N'D03: current baseline must not grant direct payment UPDATE', 1;

IF HAS_PERMS_BY_NAME(N'dbo.tc_schema_migrations', N'OBJECT', N'ALTER') = 1
    THROW 51002, N'D03: runtime baseline must not grant migration DDL', 1;
