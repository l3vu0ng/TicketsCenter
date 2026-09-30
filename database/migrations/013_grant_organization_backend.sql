SET NOCOUNT ON;
SET XACT_ABORT ON;

GRANT SELECT, INSERT ON dbo.tc_organization_requests TO tc_buyer;
GRANT SELECT ON dbo.tc_organizations TO tc_buyer;

GRANT SELECT, INSERT, UPDATE ON dbo.tc_organization_memberships TO tc_manager;
GRANT SELECT (id, normalized_email, status) ON dbo.tc_users TO tc_manager;
GRANT SELECT (id) ON dbo.tc_organizations TO tc_manager;

GRANT SELECT, UPDATE ON dbo.tc_organization_requests TO tc_platform_admin;
GRANT SELECT ON dbo.tc_organizations TO tc_platform_admin;
GRANT SELECT ON dbo.tc_organization_memberships TO tc_platform_admin;
GRANT SELECT (id, status) ON dbo.tc_users TO tc_platform_admin;
GRANT UPDATE (status, version) ON dbo.tc_users TO tc_platform_admin;
GRANT SELECT, INSERT ON dbo.tc_commission_rules TO tc_platform_admin;
GRANT INSERT ON dbo.tc_audit_logs TO tc_platform_admin;
