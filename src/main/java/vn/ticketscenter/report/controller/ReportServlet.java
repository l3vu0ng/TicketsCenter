package vn.ticketscenter.report.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.filter.AuthenticationFilter;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;
import vn.ticketscenter.report.repository.ReportRepository;
import vn.ticketscenter.report.repository.ReportRepository.EventReport;
import vn.ticketscenter.report.repository.ReportRepository.ReportFilter;
import vn.ticketscenter.report.repository.ReportRepository.ReportMetrics;
import vn.ticketscenter.report.repository.ReportRepository.ReportPage;
import vn.ticketscenter.report.service.ReportService;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@WebServlet(name = "ReportServlet", urlPatterns = "/api/admin/reports")
public final class ReportServlet extends HttpServlet {
    private final ReportService reportService;
    private final Clock clock;

    public ReportServlet() { this(null, Clock.systemUTC()); }
    public ReportServlet(ReportService reportService, Clock clock) {
        this.reportService = reportService;
        this.clock = clock;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleGet(request, response, null);
    }

    public void handleGet(HttpServletRequest request, HttpServletResponse response, UUID organizationScope) throws IOException {
        try {
            AuthenticatedAccount account = account(request);
            if (organizationScope == null && !account.admin()) throw new SecurityException("administrator role required");
            ReportPage page = service(request).get(account, organizationScope, filter(request, organizationScope, clock));
            HttpResponses.data(response, json(page));
        } catch (SecurityException exception) {
            HttpResponses.error(response, 403, "FORBIDDEN", exception.getMessage());
        } catch (IllegalArgumentException exception) {
            HttpResponses.error(response, 400, "VALIDATION_FAILED", exception.getMessage());
        }
    }

    static ReportFilter filter(HttpServletRequest request, UUID organizationScope, Clock clock) {
        Instant now = clock.instant();
        return new ReportFilter(organizationScope == null ? uuid(request.getParameter("organizationId")) : organizationScope,
                uuid(request.getParameter("eventId")), instant(request.getParameter("from"), now.minus(30, ChronoUnit.DAYS)),
                instant(request.getParameter("to"), now), integer(request.getParameter("page"), 1),
                integer(request.getParameter("pageSize"), 20), request.getParameter("sort"));
    }

    public static String json(ReportPage page) {
        return "{\"metrics\":" + metricsJson(page.metrics()) + ",\"items\":["
                + page.items().stream().map(ReportServlet::eventJson).reduce((a, b) -> a + "," + b).orElse("")
                + "],\"page\":" + page.page() + ",\"pageSize\":" + page.pageSize() + ",\"total\":" + page.total() + "}";
    }

    private static String metricsJson(ReportMetrics value) {
        return "{\"paidOrderCount\":" + value.paidOrderCount() + ",\"cohortGrossRevenue\":" + string(value.cohortGrossRevenue())
                + ",\"cohortRefundedAmount\":" + string(value.cohortRefundedAmount()) + ",\"cohortNetRevenue\":" + string(value.cohortNetRevenue())
                + ",\"ticketCapturedAmount\":" + string(value.ticketCapturedAmount()) + ",\"compensationCapturedAmount\":" + string(value.compensationCapturedAmount())
                + ",\"ticketRefundedAmount\":" + string(value.ticketRefundedAmount()) + ",\"compensationRefundedAmount\":" + string(value.compensationRefundedAmount())
                + ",\"netCashFlow\":" + string(value.netCashFlow()) + ",\"commissionAmount\":" + string(value.commissionAmount())
                + ",\"netPayable\":" + string(value.netPayable()) + "}";
    }

    private static String eventJson(EventReport value) {
        return "{\"eventId\":" + string(value.eventId()) + ",\"organizationId\":" + string(value.organizationId())
                + ",\"title\":" + string(value.title()) + ",\"status\":" + string(value.status())
                + ",\"paidOrderCount\":" + value.paidOrderCount() + ",\"activeTickets\":" + value.activeTickets()
                + ",\"usedTickets\":" + value.usedTickets() + ",\"inactiveTickets\":" + value.inactiveTickets()
                + ",\"grossRevenue\":" + string(value.grossRevenue()) + ",\"totalRefund\":" + string(value.totalRefund())
                + ",\"totalCommission\":" + string(value.totalCommission()) + ",\"netPayable\":" + string(value.netPayable())
                + ",\"settlementSnapshot\":" + value.settlementSnapshot() + "}";
    }

    private ReportService service(HttpServletRequest request) {
        if (reportService != null) return reportService;
        Object configured = request.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
        if (configured instanceof PersistenceRegistry registry) return new ReportService(registry.transactionManager(), new ReportRepository());
        throw new IllegalStateException("report service is unavailable");
    }

    private static AuthenticatedAccount account(HttpServletRequest request) {
        Object value = request.getAttribute(AuthenticationFilter.ACCOUNT_ATTRIBUTE);
        if (value instanceof AuthenticatedAccount account) return account;
        throw new SecurityException("authenticated user required");
    }

    private static UUID uuid(String value) {
        try { return value == null || value.isBlank() ? null : UUID.fromString(value); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("invalid UUID filter"); }
    }

    private static Instant instant(String value, Instant fallback) {
        try { return value == null || value.isBlank() ? fallback : Instant.parse(value); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("invalid UTC report time"); }
    }

    private static int integer(String value, int fallback) {
        try { return value == null || value.isBlank() ? fallback : Integer.parseInt(value); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("invalid pagination"); }
    }

    private static String string(Object value) {
        return value == null ? "null" : HttpResponses.jsonString(value.toString());
    }
}
