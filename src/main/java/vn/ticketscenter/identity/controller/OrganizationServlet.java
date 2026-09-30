package vn.ticketscenter.identity.controller;

import jakarta.persistence.PersistenceException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.identity.dto.OrganizationDtos.MemberCommand;
import vn.ticketscenter.identity.dto.OrganizationDtos.MemberView;
import vn.ticketscenter.identity.dto.OrganizationDtos.MembershipView;
import vn.ticketscenter.identity.dto.OrganizationDtos.OrganizationRequestCommand;
import vn.ticketscenter.identity.dto.OrganizationDtos.OrganizationRequestView;
import vn.ticketscenter.identity.dto.OrganizationDtos.Page;
import vn.ticketscenter.identity.filter.AuthenticationFilter;
import vn.ticketscenter.identity.repository.OrganizationRepository;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;
import vn.ticketscenter.identity.service.OrganizationService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@WebServlet(name = "OrganizationServlet", urlPatterns = {
        "/api/organization-requests", "/api/me/organization-requests",
        "/api/me/memberships", "/api/organizations/*"})
public final class OrganizationServlet extends HttpServlet {

    private final OrganizationService organizationService;

    public OrganizationServlet() {
        this.organizationService = null;
    }

    public OrganizationServlet(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthenticatedAccount account = account(request, response);
        if (account == null) return;
        OrganizationService service = resolveService(request, response);
        if (service == null) return;
        try {
            String path = apiPath(request);
            if ("/me/organization-requests".equals(path)) {
                Page<OrganizationRequestView> page = service.getMyRequests(
                        account, page(request, "page", 1), page(request, "pageSize", 20));
                HttpResponses.data(response, requestPageJson(page));
                return;
            }
            if ("/me/memberships".equals(path)) {
                HttpResponses.data(response, membershipsJson(service.getMyMemberships(account)));
                return;
            }
            String[] parts = parts(path);
            if (parts.length == 3 && "organizations".equals(parts[0]) && "members".equals(parts[2])) {
                HttpResponses.data(response, membersJson(service.getMembers(account, uuid(parts[1]))));
                return;
            }
            HttpResponses.error(response, 404, "NOT_FOUND", "Endpoint tổ chức không tồn tại");
        } catch (RuntimeException exception) {
            handle(response, exception);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthenticatedAccount account = account(request, response);
        if (account == null) return;
        OrganizationService service = resolveService(request, response);
        if (service == null) return;
        try {
            String path = apiPath(request);
            Map<String, String> body = JsonObjectParser.parse(request.getReader());
            if ("/organization-requests".equals(path)) {
                UUID id = service.createRequest(account, new OrganizationRequestCommand(
                        body.get("name"), body.get("contactEmail"), body.get("contactPhone"), body.get("description")));
                HttpResponses.data(response, "{\"requestId\":" + json(id) + ",\"status\":\"PENDING\"}");
                return;
            }
            String[] parts = parts(path);
            if (parts.length == 3 && "organizations".equals(parts[0]) && "members".equals(parts[2])) {
                service.addOrReactivateMember(account, uuid(parts[1]), new MemberCommand(body.get("email"), body.get("role")));
                HttpResponses.data(response, "{\"updated\":true}");
                return;
            }
            if (parts.length == 5 && "organizations".equals(parts[0]) && "members".equals(parts[2])) {
                UUID organizationId = uuid(parts[1]);
                UUID userId = uuid(parts[3]);
                switch (parts[4]) {
                    case "role" -> service.changeMemberRole(account, organizationId, userId, body.get("role"));
                    case "activate" -> service.setMemberActive(account, organizationId, userId, true);
                    case "deactivate" -> service.setMemberActive(account, organizationId, userId, false);
                    default -> {
                        HttpResponses.error(response, 404, "NOT_FOUND", "Thao tác thành viên không tồn tại");
                        return;
                    }
                }
                HttpResponses.data(response, "{\"updated\":true}");
                return;
            }
            HttpResponses.error(response, 404, "NOT_FOUND", "Endpoint tổ chức không tồn tại");
        } catch (RuntimeException exception) {
            handle(response, exception);
        }
    }

    private OrganizationService resolveService(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (organizationService != null) return organizationService;
        Object configured = request.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
        if (configured instanceof PersistenceRegistry registry) {
            return new OrganizationService(registry.transactionManager(), new OrganizationRepository());
        }
        HttpResponses.error(response, 503, "DEPENDENCY_UNAVAILABLE", "Organization service is temporarily unavailable");
        return null;
    }

    private AuthenticatedAccount account(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object value = request.getAttribute(AuthenticationFilter.ACCOUNT_ATTRIBUTE);
        if (value instanceof AuthenticatedAccount account) return account;
        HttpResponses.error(response, 401, "UNAUTHORIZED", "Yêu cầu đăng nhập để truy cập");
        return null;
    }

    static String requestPageJson(Page<OrganizationRequestView> page) {
        return "{\"items\":[" + page.items().stream().map(OrganizationServlet::requestJson).reduce((a, b) -> a + "," + b).orElse("")
                + "],\"page\":" + page.page() + ",\"pageSize\":" + page.pageSize() + ",\"total\":" + page.total() + "}";
    }

    static String requestJson(OrganizationRequestView value) {
        return "{\"id\":" + json(value.id()) + ",\"applicantId\":" + json(value.applicantId())
                + ",\"organizationId\":" + json(value.organizationId()) + ",\"name\":" + json(value.name())
                + ",\"contactEmail\":" + json(value.contactEmail()) + ",\"contactPhone\":" + json(value.contactPhone())
                + ",\"description\":" + json(value.description()) + ",\"status\":" + json(value.status())
                + ",\"requestedAt\":" + json(value.requestedAt()) + ",\"decidedAt\":" + json(value.decidedAt())
                + ",\"rejectionReason\":" + json(value.rejectionReason()) + "}";
    }

    static String membersJson(List<MemberView> members) {
        return "{\"items\":[" + members.stream().map(value ->
                "{\"userId\":" + json(value.userId()) + ",\"email\":" + json(value.email())
                        + ",\"fullName\":" + json(value.fullName()) + ",\"role\":" + json(value.role())
                        + ",\"active\":" + value.active() + ",\"userStatus\":" + json(value.userStatus()) + "}")
                .reduce((a, b) -> a + "," + b).orElse("") + "]}";
    }

    static String membershipsJson(List<MembershipView> memberships) {
        return "{\"items\":[" + memberships.stream().map(value ->
                "{\"organizationId\":" + json(value.organizationId()) + ",\"organizationName\":" + json(value.organizationName())
                        + ",\"role\":" + json(value.role()) + ",\"active\":" + value.active() + "}")
                .reduce((a, b) -> a + "," + b).orElse("") + "]}";
    }

    static void handle(HttpServletResponse response, RuntimeException exception) throws IOException {
        if (exception instanceof SecurityException) HttpResponses.error(response, 403, "FORBIDDEN", exception.getMessage());
        else if (exception instanceof NoSuchElementException) HttpResponses.error(response, 404, "NOT_FOUND", exception.getMessage());
        else if (exception instanceof IllegalArgumentException) HttpResponses.error(response, 400, "VALIDATION_FAILED", exception.getMessage());
        else if (exception instanceof IllegalStateException || exception instanceof PersistenceException)
            HttpResponses.error(response, 409, "CONFLICT", exception.getMessage());
        else throw exception;
    }

    static String apiPath(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/api") ? path.substring(4) : path;
    }

    static String[] parts(String path) {
        return java.util.Arrays.stream(path.split("/")).filter(part -> !part.isBlank()).toArray(String[]::new);
    }

    static UUID uuid(String value) {
        try { return UUID.fromString(value); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("id không hợp lệ"); }
    }

    static int page(HttpServletRequest request, String name, int fallback) {
        String value = request.getParameter(name);
        if (value == null || value.isBlank()) return fallback;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(name + " không hợp lệ"); }
    }

    static String json(Object value) {
        return value == null ? "null" : HttpResponses.jsonString(value.toString());
    }
}
