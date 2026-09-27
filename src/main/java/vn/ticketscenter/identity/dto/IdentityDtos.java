package vn.ticketscenter.identity.dto;

import vn.ticketscenter.identity.model.IdentityEnums;

import java.util.UUID;

public final class IdentityDtos {
    private IdentityDtos() {}

    public record UserProfileDto(
            UUID id,
            String email,
            String fullName,
            String phone,
            boolean platformAdmin,
            IdentityEnums.UserStatus status
    ) {}

    public record OrganizationSummaryDto(
            UUID id,
            String name,
            String legalName,
            boolean verified
    ) {}

    public record MembershipSummaryDto(
            UUID organizationId,
            String organizationName,
            IdentityEnums.OrganizationRole role,
            boolean active
    ) {}
}
