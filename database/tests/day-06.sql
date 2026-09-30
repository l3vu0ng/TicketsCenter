SET NOCOUNT ON;
SET XACT_ABORT OFF;

BEGIN TRANSACTION;
BEGIN TRY
    DECLARE @admin uniqueidentifier = NEWID(), @applicant uniqueidentifier = NEWID(),
            @request uniqueidentifier = NEWID(), @organization uniqueidentifier;

    INSERT dbo.tc_users(id, email, normalized_email, password_hash, full_name)
    VALUES (@admin, N'day06-admin@example.test', N'day06-admin@example.test', N'test', N'Day 06 Admin'),
           (@applicant, N'day06-applicant@example.test', N'day06-applicant@example.test', N'test', N'Day 06 Applicant');
    INSERT dbo.tc_user_platform_roles(user_id, role) VALUES (@admin, 'ADMIN');
    INSERT dbo.tc_organization_requests(id, applicant_id, name, contact_email)
    VALUES (@request, @applicant, N'Day 06 Organization', N'contact@example.test');

    DECLARE @approval TABLE(organization_id uniqueidentifier, status varchar(30));
    INSERT @approval EXEC dbo.usp_ApproveOrganizationRequest
        @request, @admin,
        N'{"ratePercent":5,"fixedFee":1000,"effectiveFrom":"2026-01-01","effectiveTo":"2027-01-01"}';
    SELECT @organization = organization_id FROM @approval;

    IF @organization IS NULL OR NOT EXISTS (
        SELECT 1 FROM dbo.tc_organization_requests
        WHERE id = @request AND status = 'APPROVED' AND organization_id = @organization)
        THROW 51701, N'SP01 did not approve and link the organization request', 1;
    IF NOT EXISTS (SELECT 1 FROM dbo.tc_commission_rules WHERE organization_id = @organization)
        THROW 51702, N'SP01 did not create initial commission policy', 1;
    IF NOT EXISTS (
        SELECT 1 FROM dbo.tc_organization_memberships
        WHERE organization_id = @organization AND user_id = @applicant AND role = 'MANAGER' AND active = 1)
        THROW 51703, N'SP01 did not create the initial manager', 1;

    DELETE FROM @approval;
    INSERT @approval EXEC dbo.usp_ApproveOrganizationRequest
        @request, @admin,
        N'{"ratePercent":99,"fixedFee":0,"effectiveFrom":"2026-01-01","effectiveTo":"2027-01-01"}';
    IF (SELECT organization_id FROM @approval) <> @organization
        THROW 51704, N'SP01 replay created or returned another organization', 1;

    DECLARE @blocked bit = 0;
    BEGIN TRY
        UPDATE dbo.tc_organization_memberships SET active = 0
        WHERE organization_id = @organization AND user_id = @applicant;
    END TRY
    BEGIN CATCH
        IF ERROR_NUMBER() <> 51106 THROW;
        SET @blocked = 1;
    END CATCH;
    IF @blocked = 0 THROW 51705, N'TR06 allowed removal of the last active manager', 1;

    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
