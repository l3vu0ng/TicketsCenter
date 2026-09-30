package vn.ticketscenter.admin.dto;

public final class AdminDtos {
    private AdminDtos() {}

    public record UserStatusUpdateRequest(
            java.util.UUID userId,
            boolean active
    ) {}

    public record AdminDashboardSummary(
            long totalUsers,
            long totalOrganizations,
            long totalEvents,
            long totalOrders
    ) {}
}
