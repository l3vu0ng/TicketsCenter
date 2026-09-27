SET NOCOUNT ON;
SET XACT_ABORT ON;

GRANT SELECT, INSERT, UPDATE ON dbo.tc_users TO tc_auth;
GRANT SELECT, INSERT ON dbo.tc_user_platform_roles TO tc_auth;
GRANT SELECT ON dbo.tc_organization_memberships TO tc_buyer;
