package vn.ticketscenter.admin.service;

import vn.ticketscenter.admin.dto.AdminDtos.AdminDashboardSummary;
import vn.ticketscenter.admin.dto.AdminDtos.OrganizationApprovalRequest;
import vn.ticketscenter.identity.model.OrganizationRequest;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.service.AccountService;
import vn.ticketscenter.identity.service.AuthorizationService;
import vn.ticketscenter.identity.model.IdentityEnums;
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

    /**
     * Phê duyệt hoặc từ chối yêu cầu thành lập tổ chức.
     */
    public boolean processOrganizationRequest(AccountService.AuthenticatedAccount account, OrganizationApprovalRequest request) {
        AuthorizationService.requireAdmin(account);
        if (request == null || request.requestId() == null) {
            return false;
        }

        return transactions.execute(DatabasePrincipal.ADMIN, em -> {
            OrganizationRequest req = em.find(OrganizationRequest.class, request.requestId());
            if (req == null) {
                return false;
            }
            if (request.approve()) {
                req.setStatus(IdentityEnums.OrganizationRequestStatus.APPROVED);
            } else {
                req.setStatus(IdentityEnums.OrganizationRequestStatus.REJECTED);
            }
            return true;
        });
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
            User user = em.find(User.class, targetUserId);
            if (user == null) {
                return false;
            }
            user.setStatus(active ? IdentityEnums.UserStatus.ACTIVE : IdentityEnums.UserStatus.DISABLED);
            return true;
        });
    }
}
