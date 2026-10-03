SET NOCOUNT ON;
SET XACT_ABORT ON;

EXEC(N'
CREATE OR ALTER TRIGGER dbo.trg_AuditLog_AppendOnly ON dbo.tc_audit_logs
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted)
        THROW 51110, ''Audit log is append-only'', 1;
END;
');

-- Canonical SP10 name retained by the coverage contract; the established implementation remains the single owner.
EXEC(N'
CREATE OR ALTER PROCEDURE dbo.usp_DecideRefundRequest
    @request_id uniqueidentifier,
    @actor_id uniqueidentifier,
    @decision varchar(20),
    @rejection_reason nvarchar(1000) = NULL,
    @retry_failed bit = 0
AS
BEGIN
    SET NOCOUNT ON;
    EXEC dbo.usp_ReviewRefundRequest @request_id, @actor_id, @decision, @rejection_reason, @retry_failed;
END;
');

GRANT SELECT ON dbo.tc_audit_logs TO tc_platform_admin;
GRANT SELECT (id, email, full_name, phone, status, email_verified_at) ON dbo.tc_users TO tc_buyer;
GRANT SELECT ON dbo.tc_user_platform_roles TO tc_buyer;
GRANT SELECT ON dbo.tc_organizations TO tc_manager;
GRANT SELECT ON dbo.tc_events TO tc_platform_admin;
GRANT SELECT ON dbo.tc_refund_requests TO tc_platform_admin;
GRANT SELECT ON dbo.tc_settlements TO tc_platform_admin;
GRANT SELECT (id, email, full_name) ON dbo.tc_users TO tc_platform_admin;
GRANT EXECUTE ON dbo.usp_DecideRefundRequest TO tc_platform_admin;
GRANT EXECUTE ON dbo.usp_DecideRefundRequest TO tc_worker;
