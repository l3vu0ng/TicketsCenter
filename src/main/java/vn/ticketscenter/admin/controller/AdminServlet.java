package vn.ticketscenter.admin.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.admin.dto.AdminDtos.AdminDashboardSummary;
import vn.ticketscenter.admin.dto.AdminDtos.OrganizationApprovalRequest;
import vn.ticketscenter.admin.service.AdminService;
import vn.ticketscenter.identity.service.SessionService;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.service.AccountService;
import vn.ticketscenter.identity.service.AuthorizationService;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.config.web.JsonObjectParser;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@WebServlet(name = "AdminServlet", urlPatterns = {"/api/admin", "/api/admin/*"})
public class AdminServlet extends HttpServlet {

    private final AdminService adminService;
    private final SessionService sessionService;

    public AdminServlet() {
        this.adminService = null;
        this.sessionService = new SessionService();
    }

    public AdminServlet(AdminService adminService, SessionService sessionService) {
        this.adminService = adminService;
        this.sessionService = sessionService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Optional<AccountService.AuthenticatedAccount> accountOpt = getAuthenticatedAccount(req);
        if (accountOpt.isEmpty()) {
            HttpResponses.error(resp, 401, "UNAUTHORIZED", "Yêu cầu đăng nhập để truy cập");
            return;
        }

        try {
            AuthorizationService.requireAdmin(accountOpt.get());
        } catch (SecurityException e) {
            HttpResponses.error(resp, 403, "FORBIDDEN", "Quyền Quản trị viên (ADMIN) là bắt buộc");
            return;
        }

        AdminService service = resolveService(req, resp);
        if (service == null) return;

        String pathInfo = req.getPathInfo();
        if ("/dashboard".equals(pathInfo) || pathInfo == null || "/".equals(pathInfo)) {
            AdminDashboardSummary summary = service.getDashboardSummary(accountOpt.get());
            String json = "{\"totalUsers\":" + summary.totalUsers()
                    + ",\"totalOrganizations\":" + summary.totalOrganizations()
                    + ",\"totalEvents\":" + summary.totalEvents()
                    + ",\"totalOrders\":" + summary.totalOrders() + "}";
            HttpResponses.data(resp, json);
            return;
        }

        HttpResponses.error(resp, 404, "NOT_FOUND", "Endpoint quản trị không tồn tại");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Optional<AccountService.AuthenticatedAccount> accountOpt = getAuthenticatedAccount(req);
        if (accountOpt.isEmpty()) {
            HttpResponses.error(resp, 401, "UNAUTHORIZED", "Yêu cầu đăng nhập để truy cập");
            return;
        }

        try {
            AuthorizationService.requireAdmin(accountOpt.get());
        } catch (SecurityException e) {
            HttpResponses.error(resp, 403, "FORBIDDEN", "Quyền Quản trị viên (ADMIN) là bắt buộc");
            return;
        }

        AdminService service = resolveService(req, resp);
        if (service == null) return;

        String pathInfo = req.getPathInfo();
        if ("/organizations/review".equals(pathInfo)) {
            Map<String, String> body = JsonObjectParser.parse(req.getReader());
            String reqIdStr = body.get("requestId");
            boolean approve = Boolean.parseBoolean(body.get("approve"));
            String reason = body.get("rejectionReason");

            if (reqIdStr == null || reqIdStr.isBlank()) {
                HttpResponses.error(resp, 400, "VALIDATION_FAILED", "requestId là bắt buộc");
                return;
            }

            try {
                UUID requestId = UUID.fromString(reqIdStr.trim());
                boolean processed = service.processOrganizationRequest(accountOpt.get(),
                        new OrganizationApprovalRequest(requestId, approve, reason));
                if (processed) {
                    HttpResponses.data(resp, "{\"status\":" + HttpResponses.jsonString(approve ? "APPROVED" : "REJECTED") + "}");
                } else {
                    HttpResponses.error(resp, 404, "NOT_FOUND", "Không tìm thấy yêu cầu tổ chức");
                }
            } catch (IllegalArgumentException e) {
                HttpResponses.error(resp, 400, "VALIDATION_FAILED", "requestId không hợp lệ");
            }
            return;
        }

        if ("/users/status".equals(pathInfo)) {
            Map<String, String> body = JsonObjectParser.parse(req.getReader());
            String userIdStr = body.get("userId");
            boolean active = Boolean.parseBoolean(body.get("active"));

            if (userIdStr == null || userIdStr.isBlank()) {
                HttpResponses.error(resp, 400, "VALIDATION_FAILED", "userId là bắt buộc");
                return;
            }

            try {
                UUID userId = UUID.fromString(userIdStr.trim());
                boolean updated = service.updateUserStatus(accountOpt.get(), userId, active);
                if (updated) {
                    HttpResponses.data(resp, "{\"userId\":" + HttpResponses.jsonString(userId.toString()) + ",\"active\":" + active + "}");
                } else {
                    HttpResponses.error(resp, 404, "NOT_FOUND", "Không tìm thấy người dùng");
                }
            } catch (IllegalArgumentException e) {
                HttpResponses.error(resp, 400, "VALIDATION_FAILED", "userId không hợp lệ");
            }
            return;
        }

        HttpResponses.error(resp, 404, "NOT_FOUND", "Endpoint quản trị không tồn tại");
    }

    private AdminService resolveService(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (this.adminService != null) {
            return this.adminService;
        }
        Object configured = req.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
        if (!(configured instanceof PersistenceRegistry registry)) {
            HttpResponses.error(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "DEPENDENCY_UNAVAILABLE", "Admin service is temporarily unavailable");
            return null;
        }
        return new AdminService(registry.transactionManager());
    }

    private Optional<AccountService.AuthenticatedAccount> getAuthenticatedAccount(HttpServletRequest req) {
        Object account = req.getAttribute(vn.ticketscenter.identity.filter.AuthenticationFilter.ACCOUNT_ATTRIBUTE);
        if (account instanceof AccountService.AuthenticatedAccount authenticated) {
            return Optional.of(authenticated);
        }
        return Optional.empty();
    }
}
