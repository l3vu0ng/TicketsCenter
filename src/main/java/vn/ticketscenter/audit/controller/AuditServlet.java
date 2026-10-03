package vn.ticketscenter.audit.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.audit.repository.AuditRepository;
import vn.ticketscenter.audit.repository.AuditRepository.AuditEntry;
import vn.ticketscenter.audit.repository.AuditRepository.AuditFilter;
import vn.ticketscenter.audit.repository.AuditRepository.AuditPage;
import vn.ticketscenter.audit.service.AuditService;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.filter.AuthenticationFilter;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;

@WebServlet(name = "AuditServlet", urlPatterns = {"/api/admin/audit-logs", "/api/admin/audit-logs/*"})
public final class AuditServlet extends HttpServlet {
    private final AuditService auditService;
    private final Clock clock;

    public AuditServlet() { this(null, Clock.systemUTC()); }
    public AuditServlet(AuditService auditService, Clock clock) { this.auditService = auditService; this.clock = clock; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String path = request.getPathInfo();
            AuthenticatedAccount account = account(request);
            if (path != null && !path.equals("/")) {
                HttpResponses.data(response, entryJson(service(request).getOne(account, id(path))));
                return;
            }
            Instant now = clock.instant();
            AuditPage page = service(request).get(account, new AuditFilter(request.getParameter("action"),
                    request.getParameter("aggregateType"), instant(request.getParameter("from"), now.minus(30, ChronoUnit.DAYS)),
                    instant(request.getParameter("to"), now), integer(request.getParameter("page"), 1),
                    integer(request.getParameter("pageSize"), 20)));
            HttpResponses.data(response, pageJson(page));
        } catch (SecurityException exception) {
            HttpResponses.error(response, 403, "FORBIDDEN", exception.getMessage());
        } catch (IllegalArgumentException exception) {
            HttpResponses.error(response, 400, "VALIDATION_FAILED", exception.getMessage());
        } catch (NoSuchElementException exception) {
            HttpResponses.error(response, 404, "NOT_FOUND", exception.getMessage());
        }
    }

    private AuditService service(HttpServletRequest request) {
        if (auditService != null) return auditService;
        Object configured = request.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
        if (configured instanceof PersistenceRegistry registry) return new AuditService(registry.transactionManager(), new AuditRepository());
        throw new IllegalStateException("audit service is unavailable");
    }

    private static AuthenticatedAccount account(HttpServletRequest request) {
        Object value = request.getAttribute(AuthenticationFilter.ACCOUNT_ATTRIBUTE);
        if (value instanceof AuthenticatedAccount account) return account;
        throw new SecurityException("authenticated user required");
    }

    private static String pageJson(AuditPage page) {
        return "{\"items\":[" + page.items().stream().map(AuditServlet::entryJson).reduce((a, b) -> a + "," + b).orElse("")
                + "],\"page\":" + page.page() + ",\"pageSize\":" + page.pageSize() + ",\"total\":" + page.total() + "}";
    }

    private static String entryJson(AuditEntry value) {
        return "{\"id\":" + value.id() + ",\"actorId\":" + json(value.actorId()) + ",\"actorEmail\":" + json(value.actorEmail())
                + ",\"action\":" + json(value.action()) + ",\"aggregateType\":" + json(value.aggregateType())
                + ",\"aggregateId\":" + json(value.aggregateId()) + ",\"detail\":" + json(value.detail())
                + ",\"createdAt\":" + json(value.createdAt()) + "}";
    }

    private static long id(String path) {
        try { return Long.parseLong(path.substring(1)); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("invalid audit id"); }
    }

    private static Instant instant(String value, Instant fallback) {
        try { return value == null || value.isBlank() ? fallback : Instant.parse(value); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("invalid audit time"); }
    }

    private static int integer(String value, int fallback) {
        try { return value == null || value.isBlank() ? fallback : Integer.parseInt(value); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("invalid pagination"); }
    }

    private static String json(Object value) {
        return value == null ? "null" : HttpResponses.jsonString(value.toString());
    }
}
