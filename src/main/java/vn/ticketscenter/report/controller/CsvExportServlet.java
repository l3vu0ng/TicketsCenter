package vn.ticketscenter.report.controller;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.identity.filter.AuthenticationFilter;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;
import vn.ticketscenter.report.repository.ReportRepository;
import vn.ticketscenter.report.repository.ReportRepository.EventReport;
import vn.ticketscenter.report.repository.ReportRepository.ReportFilter;
import vn.ticketscenter.report.repository.ReportRepository.ReportPage;
import vn.ticketscenter.report.service.ReportService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;

@WebServlet(name = "CsvExportServlet", urlPatterns = "/api/reports/export")
public final class CsvExportServlet extends HttpServlet {
    private static final int BATCH_SIZE = 100;
    private static final long MAX_ROWS = 10_000;
    private final ReportService reportService;
    private final Clock clock;

    public CsvExportServlet() { this(null, Clock.systemUTC()); }
    public CsvExportServlet(ReportService reportService, Clock clock) { this.reportService = reportService; this.clock = clock; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            AuthenticatedAccount account = account(request);
            UUID organizationScope = account.admin() ? null : requiredUuid(request.getParameter("organizationId"));
            ReportFilter requested = ReportServlet.filter(request, organizationScope, clock);
            response.setStatus(200);
            response.setContentType("text/csv;charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=report.csv");
            response.setHeader("Cache-Control", "no-store");
            ServletOutputStream output = response.getOutputStream();
            output.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            ReportFilter firstFilter = new ReportFilter(requested.organizationId(), requested.eventId(), requested.from(),
                    requested.to(), 1, BATCH_SIZE, requested.sort());
            ReportPage firstPage = service(request).get(account, organizationScope, firstFilter);
            write(output, summary(firstPage));
            write(output, "eventId,organizationId,eventTitle,status,paidOrderCount,activeTickets,usedTickets,inactiveTickets,grossRevenue,totalRefund,totalCommission,netPayable,settlementSnapshot\r\n");
            long written = 0;
            for (int pageNumber = 1; written < MAX_ROWS; pageNumber++) {
                ReportPage page = pageNumber == 1 ? firstPage : service(request).get(account, organizationScope,
                        new ReportFilter(requested.organizationId(), requested.eventId(), requested.from(),
                                requested.to(), pageNumber, BATCH_SIZE, requested.sort()));
                for (EventReport row : page.items()) {
                    write(output, line(row));
                    written++;
                }
                output.flush();
                if (page.items().size() < BATCH_SIZE || written >= page.total()) break;
            }
        } catch (SecurityException exception) {
            response.sendError(403, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            response.sendError(400, exception.getMessage());
        }
    }

    public static String textCell(String value) {
        String safe = value == null ? "" : value;
        int first = 0;
        while (first < safe.length() && (Character.isWhitespace(safe.charAt(first)) || Character.isISOControl(safe.charAt(first)))) first++;
        if (first < safe.length() && "=+-@".indexOf(safe.charAt(first)) >= 0) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    public static String numberCell(String value) {
        if (value == null || !value.matches("-?\\d+")) throw new IllegalArgumentException("invalid server-generated number");
        return value;
    }

    private static String line(EventReport value) {
        return textCell(value.eventId().toString()) + ',' + textCell(value.organizationId().toString()) + ','
                + textCell(value.title()) + ',' + textCell(value.status()) + ',' + value.paidOrderCount() + ','
                + value.activeTickets() + ',' + value.usedTickets() + ',' + value.inactiveTickets() + ','
                + numberCell(value.grossRevenue().toPlainString()) + ',' + numberCell(value.totalRefund().toPlainString()) + ','
                + numberCell(value.totalCommission().toPlainString()) + ',' + numberCell(value.netPayable().toPlainString()) + ','
                + value.settlementSnapshot() + "\r\n";
    }

    private static String summary(ReportPage page) {
        var value = page.metrics();
        return "metric,value\r\n"
                + "paidOrderCount," + value.paidOrderCount() + "\r\n"
                + "cohortGrossRevenue," + numberCell(value.cohortGrossRevenue().toPlainString()) + "\r\n"
                + "cohortRefundedAmount," + numberCell(value.cohortRefundedAmount().toPlainString()) + "\r\n"
                + "cohortNetRevenue," + numberCell(value.cohortNetRevenue().toPlainString()) + "\r\n"
                + "ticketCapturedAmount," + numberCell(value.ticketCapturedAmount().toPlainString()) + "\r\n"
                + "compensationCapturedAmount," + numberCell(value.compensationCapturedAmount().toPlainString()) + "\r\n"
                + "ticketRefundedAmount," + numberCell(value.ticketRefundedAmount().toPlainString()) + "\r\n"
                + "compensationRefundedAmount," + numberCell(value.compensationRefundedAmount().toPlainString()) + "\r\n"
                + "netCashFlow," + numberCell(value.netCashFlow().toPlainString()) + "\r\n"
                + "commissionAmount," + numberCell(value.commissionAmount().toPlainString()) + "\r\n"
                + "netPayable," + numberCell(value.netPayable().toPlainString()) + "\r\n\r\n";
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

    private static UUID requiredUuid(String value) {
        try { return UUID.fromString(value); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("organizationId is required for manager export"); }
    }

    private static void write(ServletOutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }
}
