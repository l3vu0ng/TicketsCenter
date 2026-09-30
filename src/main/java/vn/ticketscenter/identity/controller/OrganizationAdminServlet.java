package vn.ticketscenter.identity.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.identity.dto.OrganizationDtos.CommissionRuleCommand;
import vn.ticketscenter.identity.dto.OrganizationDtos.CommissionRuleView;
import vn.ticketscenter.identity.filter.AuthenticationFilter;
import vn.ticketscenter.identity.repository.OrganizationRepository;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;
import vn.ticketscenter.identity.service.OrganizationService;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@WebServlet(name = "OrganizationAdminServlet", urlPatterns = {
        "/api/admin/organization-requests", "/api/admin/organization-requests/*",
        "/api/admin/organizations/*"})
public final class OrganizationAdminServlet extends HttpServlet {

    private final OrganizationService organizationService;

    public OrganizationAdminServlet() {
        this.organizationService = null;
    }

    public OrganizationAdminServlet(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthenticatedAccount account = account(request, response);
        if (account == null) return;
        OrganizationService service = resolveService(request, response);
        if (service == null) return;
        try {
            String path = OrganizationServlet.apiPath(request);
            String[] parts = OrganizationServlet.parts(path);
            if (parts.length == 2 && "organization-requests".equals(parts[1])) {
                HttpResponses.data(response, OrganizationServlet.requestPageJson(service.getAdminRequests(
                        account, request.getParameter("status"), OrganizationServlet.page(request, "page", 1),
                        OrganizationServlet.page(request, "pageSize", 20))));
                return;
            }
            if (parts.length == 3 && "organization-requests".equals(parts[1])) {
                HttpResponses.data(response, OrganizationServlet.requestJson(service.getAdminRequest(account, OrganizationServlet.uuid(parts[2]))));
                return;
            }
            if (parts.length == 4 && "organizations".equals(parts[1]) && "commission-rules".equals(parts[3])) {
                HttpResponses.data(response, commissionRulesJson(service.getCommissionRules(account, OrganizationServlet.uuid(parts[2]))));
                return;
            }
            HttpResponses.error(response, 404, "NOT_FOUND", "Endpoint quản trị tổ chức không tồn tại");
        } catch (RuntimeException exception) {
            OrganizationServlet.handle(response, exception);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthenticatedAccount account = account(request, response);
        if (account == null) return;
        OrganizationService service = resolveService(request, response);
        if (service == null) return;
        try {
            String[] parts = OrganizationServlet.parts(OrganizationServlet.apiPath(request));
            Map<String, String> body = JsonObjectParser.parse(request.getReader());
            if (parts.length == 4 && "organization-requests".equals(parts[1])) {
                UUID requestId = OrganizationServlet.uuid(parts[2]);
                if ("approve".equals(parts[3])) {
                    UUID organizationId = service.approveRequest(account, requestId);
                    HttpResponses.data(response, "{\"requestId\":" + OrganizationServlet.json(requestId)
                            + ",\"organizationId\":" + OrganizationServlet.json(organizationId) + ",\"status\":\"APPROVED\"}");
                    return;
                }
                if ("reject".equals(parts[3])) {
                    service.rejectRequest(account, requestId, body.get("reason"));
                    HttpResponses.data(response, "{\"requestId\":" + OrganizationServlet.json(requestId) + ",\"status\":\"REJECTED\"}");
                    return;
                }
            }
            if (parts.length == 4 && "organizations".equals(parts[1]) && "commission-rules".equals(parts[3])) {
                UUID ruleId = service.createCommissionRule(account, OrganizationServlet.uuid(parts[2]), new CommissionRuleCommand(
                        decimal(body.get("ratePercent")), decimal(body.get("fixedFee")),
                        instant(body.get("effectiveFrom")), instant(body.get("effectiveTo"))));
                HttpResponses.data(response, "{\"commissionRuleId\":" + OrganizationServlet.json(ruleId) + "}");
                return;
            }
            HttpResponses.error(response, 404, "NOT_FOUND", "Endpoint quản trị tổ chức không tồn tại");
        } catch (RuntimeException exception) {
            OrganizationServlet.handle(response, exception);
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

    private static String commissionRulesJson(List<CommissionRuleView> rules) {
        return "{\"items\":[" + rules.stream().map(value ->
                "{\"id\":" + OrganizationServlet.json(value.id()) + ",\"organizationId\":" + OrganizationServlet.json(value.organizationId())
                        + ",\"ratePercent\":" + OrganizationServlet.json(value.ratePercent()) + ",\"fixedFee\":" + OrganizationServlet.json(value.fixedFee())
                        + ",\"effectiveFrom\":" + OrganizationServlet.json(value.effectiveFrom()) + ",\"effectiveTo\":" + OrganizationServlet.json(value.effectiveTo()) + "}")
                .reduce((a, b) -> a + "," + b).orElse("") + "]}";
    }

    private static BigDecimal decimal(String value) {
        try { return value == null ? null : new BigDecimal(value); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Số tiền hoặc tỷ lệ không hợp lệ"); }
    }

    private static Instant instant(String value) {
        try { return value == null ? null : Instant.parse(value); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Thời gian không hợp lệ"); }
    }
}
