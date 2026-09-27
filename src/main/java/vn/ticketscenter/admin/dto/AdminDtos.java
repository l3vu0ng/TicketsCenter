package vn.ticketscenter.admin.dto;

import java.util.UUID;

public final class AdminDtos {
    private AdminDtos() {}

    public record OrganizationApprovalRequest(
            UUID requestId,
            boolean approve,
            String rejectionReason
    ) {}

    public record UserStatusUpdateRequest(
            UUID userId,
            boolean active
    ) {}

    public record AdminDashboardSummary(
            long totalUsers,
            long totalOrganizations,
            long totalEvents,
            long totalOrders
    ) {}
}
