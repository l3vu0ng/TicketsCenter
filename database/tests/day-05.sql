SET NOCOUNT ON;

IF NOT EXISTS (
    SELECT 1 FROM sys.database_permissions permission
    JOIN sys.database_principals principal ON principal.principal_id = permission.grantee_principal_id
    WHERE principal.name = N'tc_auth' AND permission.permission_name = N'SELECT'
      AND permission.major_id = OBJECT_ID(N'dbo.tc_otps')
)
    THROW 51000, N'D05: tc_auth must read otps', 1;

IF NOT EXISTS (
    SELECT 1 FROM sys.database_permissions permission
    JOIN sys.database_principals principal ON principal.principal_id = permission.grantee_principal_id
    WHERE principal.name = N'tc_auth' AND permission.permission_name = N'INSERT'
      AND permission.major_id = OBJECT_ID(N'dbo.tc_otps')
)
    THROW 51001, N'D05: tc_auth must insert otps', 1;

IF NOT EXISTS (
    SELECT 1 FROM sys.database_permissions permission
    JOIN sys.database_principals principal ON principal.principal_id = permission.grantee_principal_id
    WHERE principal.name = N'tc_auth' AND permission.permission_name = N'UPDATE'
      AND permission.major_id = OBJECT_ID(N'dbo.tc_otps')
)
    THROW 51002, N'D05: tc_auth must update otps', 1;

IF EXISTS (
    SELECT 1 FROM sys.database_permissions permission
    JOIN sys.database_principals principal ON principal.principal_id = permission.grantee_principal_id
    WHERE principal.name = N'tc_auth' AND permission.permission_name = N'DELETE'
      AND permission.major_id = OBJECT_ID(N'dbo.tc_otps')
)
    THROW 51003, N'D05: tc_auth must not delete otps', 1;
