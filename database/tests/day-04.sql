SET NOCOUNT ON;

IF NOT EXISTS (
    SELECT 1 FROM sys.database_permissions permission
    JOIN sys.database_principals principal ON principal.principal_id = permission.grantee_principal_id
    WHERE principal.name = N'tc_auth' AND permission.permission_name = N'SELECT'
      AND permission.major_id = OBJECT_ID(N'dbo.tc_users')
)
    THROW 51000, N'D04: tc_auth must read users', 1;

IF EXISTS (
    SELECT 1 FROM sys.database_permissions permission
    JOIN sys.database_principals principal ON principal.principal_id = permission.grantee_principal_id
    WHERE principal.name IN (N'tc_auth', N'tc_buyer') AND permission.permission_name = N'DELETE'
)
    THROW 51001, N'D04: runtime auth roles must not delete data', 1;
