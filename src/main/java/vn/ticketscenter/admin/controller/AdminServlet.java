package vn.ticketscenter.admin.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.admin.dto.AdminDtos.AdminDashboardSummary;
import vn.ticketscenter.admin.service.AdminService;
import vn.ticketscenter.identity.service.SessionService;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.service.AccountService;
import vn.ticketscenter.identity.service.AuthorizationService;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.event.repository.EventRepository;
import vn.ticketscenter.event.repository.EventCancellationRepository;
import vn.ticketscenter.event.controller.EventCancellationServlet;
import vn.ticketscenter.event.service.EventService;
import vn.ticketscenter.event.service.EventCancellationService;

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
        String[] parts = pathInfo == null ? new String[0] : java.util.Arrays.stream(pathInfo.split("/"))
                .filter(part -> !part.isBlank()).toArray(String[]::new);
        if (parts.length == 3 && "events".equals(parts[0]) && "cancellation-progress".equals(parts[2])) {
            EventCancellationServlet cancellation = resolveCancellationServlet(req, resp);
            if (cancellation != null) cancellation.handleGet(req, resp);
            return;
        }
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
        String[] parts = pathInfo == null ? new String[0] : java.util.Arrays.stream(pathInfo.split("/"))
                .filter(part -> !part.isBlank()).toArray(String[]::new);
        if (parts.length == 3 && "events".equals(parts[0]) && "cancel".equals(parts[2])) {
            EventCancellationServlet cancellation = resolveCancellationServlet(req, resp);
            if (cancellation != null) cancellation.handlePost(req, resp);
            return;
        }
        if (parts.length == 3 && "events".equals(parts[0])) {
            try {
                UUID eventId = UUID.fromString(parts[1]);
                Map<String, String> body = JsonObjectParser.parse(req.getReader());
                EventService events = resolveEventService(req, resp);
                if (events == null) return;
                if ("publish".equals(parts[2])) {
                    String ruleId = body.get("commissionRuleId");
                    if (ruleId == null) throw new IllegalArgumentException("commissionRuleId là bắt buộc");
                    events.publish(accountOpt.get(), eventId, UUID.fromString(ruleId));
                    HttpResponses.data(resp, "{\"eventId\":" + HttpResponses.jsonString(eventId.toString())
                            + ",\"status\":\"PUBLISHED\"}");
                } else if ("reject".equals(parts[2])) {
                    events.reject(accountOpt.get(), eventId, body.get("reason"));
                    HttpResponses.data(resp, "{\"eventId\":" + HttpResponses.jsonString(eventId.toString())
                            + ",\"status\":\"REJECTED\"}");
                } else {
                    HttpResponses.error(resp, 404, "NOT_FOUND", "Thao tác sự kiện không tồn tại");
                }
            } catch (IllegalArgumentException exception) {
                HttpResponses.error(resp, 400, "VALIDATION_FAILED", exception.getMessage());
            } catch (IllegalStateException exception) {
                HttpResponses.error(resp, 409, "CONFLICT", exception.getMessage());
            } catch (jakarta.persistence.PersistenceException exception) {
                HttpResponses.error(resp, 409, "CONFLICT", "Không thể cập nhật trạng thái sự kiện");
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

    private EventService resolveEventService(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Object configured = req.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
        if (configured instanceof PersistenceRegistry registry) {
            return new EventService(registry.transactionManager(), new EventRepository());
        }
        HttpResponses.error(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                "DEPENDENCY_UNAVAILABLE", "Event service is temporarily unavailable");
        return null;
    }

    private EventCancellationServlet resolveCancellationServlet(HttpServletRequest req,
                                                                HttpServletResponse resp) throws IOException {
        Object configured = req.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
        if (configured instanceof PersistenceRegistry registry) {
            return new EventCancellationServlet(new EventCancellationService(
                    registry.transactionManager(), new EventCancellationRepository()));
        }
        HttpResponses.error(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                "DEPENDENCY_UNAVAILABLE", "Event cancellation service is temporarily unavailable");
        return null;
    }

    private Optional<AccountService.AuthenticatedAccount> getAuthenticatedAccount(HttpServletRequest req) {
        Object account = req.getAttribute(vn.ticketscenter.identity.filter.AuthenticationFilter.ACCOUNT_ATTRIBUTE);
        if (account instanceof AccountService.AuthenticatedAccount authenticated) {
            return Optional.of(authenticated);
        }
        return Optional.empty();
    }
}
