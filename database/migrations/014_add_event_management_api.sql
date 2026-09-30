SET NOCOUNT ON;
SET XACT_ABORT ON;

IF COL_LENGTH(N'dbo.tc_events', N'rejection_reason') IS NULL
    ALTER TABLE dbo.tc_events ADD rejection_reason nvarchar(1000) NULL;

GRANT SELECT ON dbo.tc_event_categories TO tc_buyer;
GRANT SELECT ON dbo.tc_event_categories TO tc_manager;

GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.tc_events TO tc_manager;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.tc_zones TO tc_manager;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.tc_seats TO tc_manager;
GRANT SELECT ON dbo.tc_event_categories TO tc_platform_admin;
GRANT SELECT, UPDATE ON dbo.tc_events TO tc_platform_admin;
