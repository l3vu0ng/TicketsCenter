:setvar BUYER_LOGIN "tc_buyer_login"
:setvar MANAGER_LOGIN "tc_manager_login"
:setvar CHECKIN_LOGIN "tc_checkin_login"
:setvar ADMIN_LOGIN "tc_admin_login"
:setvar AUTH_LOGIN "tc_auth_login"
:setvar WORKER_LOGIN "tc_worker_login"

-- Password variables must be supplied securely to sqlcmd; never commit values.
:setvar BUYER_PASSWORD "$(TC_BUYER_DB_PASSWORD)"
:setvar MANAGER_PASSWORD "$(TC_MANAGER_DB_PASSWORD)"
:setvar CHECKIN_PASSWORD "$(TC_CHECKIN_DB_PASSWORD)"
:setvar ADMIN_PASSWORD "$(TC_ADMIN_DB_PASSWORD)"
:setvar AUTH_PASSWORD "$(TC_AUTH_DB_PASSWORD)"
:setvar WORKER_PASSWORD "$(TC_WORKER_DB_PASSWORD)"

DECLARE @principals table (login_name sysname, user_name sysname, role_name sysname, secret nvarchar(256));
INSERT @principals VALUES
    (N'$(BUYER_LOGIN)', N'tc_buyer_user', N'tc_buyer', N'$(BUYER_PASSWORD)'),
    (N'$(MANAGER_LOGIN)', N'tc_manager_user', N'tc_manager', N'$(MANAGER_PASSWORD)'),
    (N'$(CHECKIN_LOGIN)', N'tc_checkin_user', N'tc_checkin', N'$(CHECKIN_PASSWORD)'),
    (N'$(ADMIN_LOGIN)', N'tc_admin_user', N'tc_platform_admin', N'$(ADMIN_PASSWORD)'),
    (N'$(AUTH_LOGIN)', N'tc_auth_user', N'tc_auth', N'$(AUTH_PASSWORD)'),
    (N'$(WORKER_LOGIN)', N'tc_worker_user', N'tc_worker', N'$(WORKER_PASSWORD)');

DECLARE @login sysname, @user sysname, @role sysname, @secret nvarchar(256), @sql nvarchar(max);
DECLARE principal_cursor CURSOR LOCAL FAST_FORWARD FOR SELECT login_name, user_name, role_name, secret FROM @principals;
OPEN principal_cursor;
FETCH NEXT FROM principal_cursor INTO @login, @user, @role, @secret;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF @secret IS NULL OR @secret = N'' OR @secret LIKE N'$(TC_%'
        THROW 51000, N'Missing runtime database password', 1;
    IF SUSER_ID(@login) IS NULL
    BEGIN
        SET @sql = N'CREATE LOGIN ' + QUOTENAME(@login) + N' WITH PASSWORD = ' + QUOTENAME(@secret, '''') + N', CHECK_POLICY = ON;';
        EXEC sys.sp_executesql @sql;
    END;
    IF DATABASE_PRINCIPAL_ID(@user) IS NULL
    BEGIN
        SET @sql = N'CREATE USER ' + QUOTENAME(@user) + N' FOR LOGIN ' + QUOTENAME(@login) + N';';
        EXEC sys.sp_executesql @sql;
    END;
    IF NOT EXISTS (SELECT 1 FROM sys.database_role_members rm
                   JOIN sys.database_principals r ON r.principal_id = rm.role_principal_id
                   JOIN sys.database_principals u ON u.principal_id = rm.member_principal_id
                   WHERE r.name = @role AND u.name = @user)
    BEGIN
        SET @sql = N'ALTER ROLE ' + QUOTENAME(@role) + N' ADD MEMBER ' + QUOTENAME(@user) + N';';
        EXEC sys.sp_executesql @sql;
    END;
    FETCH NEXT FROM principal_cursor INTO @login, @user, @role, @secret;
END;
CLOSE principal_cursor;
DEALLOCATE principal_cursor;

-- Runtime identities deliberately never receive db_owner or sysadmin.  DDL is
-- provisioned separately by tc_migration during the migration window.
