package vn.ticketscenter.identity.service;

import vn.ticketscenter.identity.model.IdentityEnums;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Dịch vụ kiểm soát phân quyền dựa trên SPEC §14.10 (ADMIN, MANAGER, STAFF, CUSTOMER).
 */
public final class AuthorizationService {

    private final TransactionManager transactions;

    public AuthorizationService(TransactionManager transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
    }

    /**
     * Xác thực quyền Quản trị nền tảng (Platform Admin).
     */
    public static void requireAdmin(AccountService.AuthenticatedAccount account) {
        if (account == null || !account.admin()) {
            throw new SecurityException("admin role is required");
        }
    }

    /**
     * Xác thực quyền Chủ sở hữu tài nguyên (Resource Owner / Customer).
     */
    public static void requireOwner(UUID actorId, UUID ownerId) {
        if (actorId == null || !actorId.equals(ownerId)) {
            throw new SecurityException("resource is outside actor scope");
        }
    }

    /**
     * Xác thực người dùng thông thường (Customer / Buyer).
     */
    public static void requireCustomer(UUID actorId) {
        if (actorId == null) {
            throw new SecurityException("authenticated user is required");
        }
    }

    /**
     * Xác thực vai trò Quản lý tổ chức (MANAGER).
     */
    public void requireManager(UUID actorId, UUID organizationId) {
        requireActiveMembership(actorId, organizationId, Set.of(IdentityEnums.OrganizationRole.MANAGER));
    }

    /**
     * Xác thực vai trò Nhân viên check-in hoặc Quản lý của tổ chức (STAFF / MANAGER).
     */
    public void requireStaffOrManager(UUID actorId, UUID organizationId) {
        requireActiveMembership(actorId, organizationId, Set.of(
                IdentityEnums.OrganizationRole.MANAGER,
                IdentityEnums.OrganizationRole.CHECK_IN_STAFF
        ));
    }

    /**
     * Kiểm tra membership đang hoạt động với tập vai trò cho phép trong tổ chức.
     */
    public void requireActiveMembership(UUID actorId, UUID organizationId, Set<IdentityEnums.OrganizationRole> roles) {
        if (actorId == null || organizationId == null || roles == null || roles.isEmpty()) {
            throw new SecurityException("active organization membership is required");
        }
        boolean allowed = transactions.execute(DatabasePrincipal.BUYER, entityManager ->
                entityManager.createQuery("""
                                select count(m) from OrganizationMembership m
                                where m.user.id = :actorId and m.organization.id = :organizationId
                                  and m.active = true and m.role in :roles
                                """, Long.class)
                        .setParameter("actorId", actorId)
                        .setParameter("organizationId", organizationId)
                        .setParameter("roles", roles)
                        .getSingleResult() > 0);
        if (!allowed) {
            throw new SecurityException("active organization membership is required");
        }
    }

    /**
     * Ánh xạ ngữ cảnh quyền người dùng sang DatabasePrincipal tương ứng theo SPEC §14.10.
     */
    public DatabasePrincipal resolvePrincipal(AccountService.AuthenticatedAccount account, UUID organizationId) {
        if (account == null) {
            return DatabasePrincipal.BUYER;
        }
        if (account.admin()) {
            return DatabasePrincipal.ADMIN;
        }
        if (organizationId != null) {
            try {
                requireManager(account.id(), organizationId);
                return DatabasePrincipal.MANAGER;
            } catch (SecurityException ignored) {
                try {
                    requireStaffOrManager(account.id(), organizationId);
                    return DatabasePrincipal.CHECK_IN;
                } catch (SecurityException alsoIgnored) {
                    return DatabasePrincipal.BUYER;
                }
            }
        }
        return DatabasePrincipal.BUYER;
    }
}
