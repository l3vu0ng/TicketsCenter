package vn.ticketscenter.identity.model;

public final class IdentityEnums {
    private IdentityEnums() {}

    public enum UserStatus { ACTIVE, DISABLED }
    public enum OrganizationRole { MANAGER, CHECK_IN_STAFF }
    public enum OrganizationRequestStatus { PENDING, APPROVED, REJECTED }
    public enum OtpPurpose { VERIFY_EMAIL, RESET_PASSWORD }
}
