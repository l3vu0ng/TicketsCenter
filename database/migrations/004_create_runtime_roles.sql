SET NOCOUNT ON;
SET XACT_ABORT ON;

IF DATABASE_PRINCIPAL_ID(N'tc_buyer') IS NULL CREATE ROLE tc_buyer;
IF DATABASE_PRINCIPAL_ID(N'tc_manager') IS NULL CREATE ROLE tc_manager;
IF DATABASE_PRINCIPAL_ID(N'tc_checkin') IS NULL CREATE ROLE tc_checkin;
IF DATABASE_PRINCIPAL_ID(N'tc_platform_admin') IS NULL CREATE ROLE tc_platform_admin;
IF DATABASE_PRINCIPAL_ID(N'tc_auth') IS NULL CREATE ROLE tc_auth;
IF DATABASE_PRINCIPAL_ID(N'tc_worker') IS NULL CREATE ROLE tc_worker;

-- Object grants are added with the corresponding view/procedure migration.
-- Runtime roles deliberately receive no broad table DML or DDL permissions.
