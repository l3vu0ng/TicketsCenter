package vn.ticketscenter.admin.service;

import vn.ticketscenter.admin.dto.AdminDtos.AdminDashboardSummary;
import vn.ticketscenter.admin.dto.AdminDtos.AdminOverview;
import vn.ticketscenter.identity.service.AccountService;
import vn.ticketscenter.identity.service.AuthorizationService;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;

import java.util.Objects;
import java.util.UUID;

/**
 * Xử lý các nghiệp vụ quản trị nền tảng (Platform Admin) tuân thủ SPEC §14.10.
 */
public class AdminService {

    private final TransactionManager transactions;

    public AdminService(TransactionManager transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
    }

    /**
     * Lấy số liệu tổng quan hệ thống dành riêng cho tài khoản quản trị (ADMIN).
     */
    public AdminDashboardSummary getDashboardSummary(AccountService.AuthenticatedAccount account) {
        AuthorizationService.requireAdmin(account);

        return transactions.execute(DatabasePrincipal.ADMIN, em -> {
            long totalUsers = em.createQuery("select count(u) from User u", Long.class).getSingleResult();
            long totalOrgs = em.createQuery("select count(o) from Organization o", Long.class).getSingleResult();
            long totalEvents = em.createQuery("select count(e) from Event e", Long.class).getSingleResult();
            long totalOrders = em.createQuery("select count(o) from Order o", Long.class).getSingleResult();

            return new AdminDashboardSummary(totalUsers, totalOrgs, totalEvents, totalOrders);
        });
    }

    public AdminOverview getOverview(AccountService.AuthenticatedAccount account) {
        AuthorizationService.requireAdmin(account);
        return transactions.execute(DatabasePrincipal.ADMIN, entityManager -> new AdminOverview(
                count(entityManager, "SELECT COUNT_BIG(*) FROM dbo.tc_organization_requests WHERE status = 'PENDING'"),
                count(entityManager, "SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE status = 'PENDING_APPROVAL'"),
                count(entityManager, "SELECT COUNT_BIG(*) FROM dbo.tc_refund_requests WHERE status IN ('PENDING','APPROVED')"),
                count(entityManager, """
                        SELECT COUNT_BIG(*) FROM dbo.tc_events e
                        LEFT JOIN dbo.tc_settlements s ON s.event_id = e.id
                        WHERE e.end_time <= SYSUTCDATETIME() AND (s.id IS NULL OR s.status = 'DRAFT')
                        """)));
    }

    /**
     * Khóa hoặc kích hoạt tài khoản người dùng.
     */
    public boolean updateUserStatus(AccountService.AuthenticatedAccount account, UUID targetUserId, boolean active) {
        AuthorizationService.requireAdmin(account);
        if (targetUserId == null) {
            return false;
        }

        return transactions.execute(DatabasePrincipal.ADMIN, em -> {
            @SuppressWarnings("unchecked") var users = (java.util.List<UUID>) em.createNativeQuery(
                            "SELECT id FROM dbo.tc_users WITH (UPDLOCK, HOLDLOCK) WHERE id = :userId", UUID.class)
                    .setParameter("userId", targetUserId).getResultList();
            if (users.isEmpty()) {
                return false;
            }
            if (!active) {
                @SuppressWarnings("unchecked") var organizations = (java.util.List<UUID>) em.createNativeQuery("""
                                SELECT organization_id
                                FROM dbo.tc_organization_memberships
                                WHERE user_id = :userId AND role = 'MANAGER' AND active = 1
                                ORDER BY organization_id
                                """, UUID.class)
                        .setParameter("userId", targetUserId)
                        .getResultList();
                for (UUID organizationId : organizations) {
                    em.createNativeQuery("SELECT id FROM dbo.tc_organizations WITH (UPDLOCK, HOLDLOCK) WHERE id = :id")
                            .setParameter("id", organizationId).getSingleResult();
                    Number remaining = (Number) em.createNativeQuery("""
                                    SELECT COUNT_BIG(*)
                                    FROM dbo.tc_organization_memberships m WITH (UPDLOCK, HOLDLOCK)
                                    JOIN dbo.tc_users u ON u.id = m.user_id
                                    WHERE m.organization_id = :organizationId AND m.role = 'MANAGER'
                                      AND m.active = 1 AND u.status = 'ACTIVE' AND m.user_id <> :userId
                                    """)
                            .setParameter("organizationId", organizationId)
                            .setParameter("userId", targetUserId)
                            .getSingleResult();
                    if (remaining.longValue() == 0) {
                        throw new IllegalStateException("cannot disable the last active organization manager");
                    }
                }
            }
            em.createNativeQuery("""
                            UPDATE dbo.tc_users SET status = :status, version = version + 1 WHERE id = :userId
                            """)
                    .setParameter("status", active ? "ACTIVE" : "DISABLED")
                    .setParameter("userId", targetUserId)
                    .executeUpdate();
            return true;
        });
    }

    private static long count(jakarta.persistence.EntityManager entityManager, String sql) {
        return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).longValue();
    }
}
