-- Supply contained user names through sqlcmd variables. Authentication details
-- are provisioned by Azure/Entra outside this repository.
:setvar BUYER_USER "tc_buyer_user"
:setvar MANAGER_USER "tc_manager_user"
:setvar CHECKIN_USER "tc_checkin_user"
:setvar ADMIN_USER "tc_admin_user"
:setvar AUTH_USER "tc_auth_user"
:setvar WORKER_USER "tc_worker_user"

DECLARE @principals table (user_name sysname, role_name sysname);
INSERT @principals VALUES
    (N'$(BUYER_USER)', N'tc_buyer'),
    (N'$(MANAGER_USER)', N'tc_manager'),
    (N'$(CHECKIN_USER)', N'tc_checkin'),
    (N'$(ADMIN_USER)', N'tc_platform_admin'),
    (N'$(AUTH_USER)', N'tc_auth'),
    (N'$(WORKER_USER)', N'tc_worker');

DECLARE @user sysname, @role sysname, @sql nvarchar(max);
DECLARE principal_cursor CURSOR LOCAL FAST_FORWARD FOR SELECT user_name, role_name FROM @principals;
OPEN principal_cursor;
FETCH NEXT FROM principal_cursor INTO @user, @role;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF DATABASE_PRINCIPAL_ID(@user) IS NULL
    BEGIN
        SET @sql = N'CREATE USER ' + QUOTENAME(@user) + N' FROM EXTERNAL PROVIDER;';
        EXEC sys.sp_executesql @sql;
    END;
    SET @sql = N'ALTER ROLE ' + QUOTENAME(@role) + N' ADD MEMBER ' + QUOTENAME(@user) + N';';
    EXEC sys.sp_executesql @sql;
    FETCH NEXT FROM principal_cursor INTO @user, @role;
END;
CLOSE principal_cursor;
DEALLOCATE principal_cursor;
