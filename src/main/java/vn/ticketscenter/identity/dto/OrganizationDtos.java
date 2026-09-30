package vn.ticketscenter.identity.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrganizationDtos {
    private OrganizationDtos() {}

    public record OrganizationRequestCommand(String name, String contactEmail, String contactPhone, String description) {}

    public record OrganizationRequestView(
            UUID id, UUID applicantId, UUID reviewerId, UUID organizationId, String name,
            String contactEmail, String contactPhone, String description, String status,
            Instant requestedAt, Instant decidedAt, String rejectionReason) {}

    public record MemberCommand(String email, String role) {}

    public record MemberView(
            UUID membershipId, UUID organizationId, UUID userId, String email,
            String fullName, String role, boolean active, String userStatus, Instant joinedAt) {}

    public record MembershipView(UUID organizationId, String organizationName, String role, boolean active) {}

    public record CommissionRuleCommand(
            BigDecimal ratePercent, BigDecimal fixedFee, Instant effectiveFrom, Instant effectiveTo) {}

    public record CommissionRuleView(
            UUID id, UUID organizationId, BigDecimal ratePercent, BigDecimal fixedFee,
            Instant effectiveFrom, Instant effectiveTo) {}

    public record Page<T>(List<T> items, int page, int pageSize, long total) {
        public Page {
            items = List.copyOf(items);
        }
    }
}
