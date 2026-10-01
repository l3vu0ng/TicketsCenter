package vn.ticketscenter.fulfillment.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.fulfillment.repository.OperationsRepository;
import vn.ticketscenter.fulfillment.repository.RefundRepository;
import vn.ticketscenter.fulfillment.service.OperationsService;
import vn.ticketscenter.fulfillment.service.RefundService;
import vn.ticketscenter.identity.filter.AuthenticationFilter;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet(name = "OperationsServlet", urlPatterns = {
        "/api/check-ins", "/api/refund-requests", "/api/admin/refund-requests/*", "/api/admin/payments/*"})
public final class OperationsServlet extends HttpServlet {
    private final OperationsService service;
    private final RefundService configuredRefundService;

    public OperationsServlet() {
        service = null;
        configuredRefundService = null;
    }

    OperationsServlet(OperationsService service) {
        this.service = service;
        configuredRefundService = null;
    }

    OperationsServlet(OperationsService service, RefundService refundService) {
        this.service = service;
        this.configuredRefundService = refundService;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthenticatedAccount account = account(request, response);
        if (account == null) return;
        OperationsService operations = resolve(request, response);
        if (operations == null) return;
        try {
            String path = request.getPathInfo();
            String servlet = request.getServletPath();
            if (servlet.equals("/api/admin/refund-requests") && path.endsWith("/retry")) {
                refundService(request).retryCustomer(account, pathId(path, "/retry"));
                HttpResponses.data(response, "{\"status\":\"PENDING\"}");
                return;
            }
            if (servlet.equals("/api/admin/payments") && path.endsWith("/compensation/retry")) {
                refundService(request).retryCompensation(account, pathId(path, "/compensation/retry"));
                HttpResponses.data(response, "{\"status\":\"PENDING\"}");
                return;
            }

            Map<String, String> body = JsonObjectParser.parse(request.getReader());
            if (servlet.equals("/api/check-ins")) {
                var result = operations.checkIn(account, uuid(body.get("eventId")), body.get("ticketCode"));
                HttpResponses.data(response, "{\"result\":" + HttpResponses.jsonString(result.result())
                        + ",\"ticketId\":" + (result.ticketId() == null ? "null" : HttpResponses.jsonString(result.ticketId().toString()))
                        + ",\"scannedAt\":" + HttpResponses.jsonString(result.scannedAt().toString()) + "}");
                return;
            }
            if (servlet.equals("/api/refund-requests")) {
                ArrayList<UUID> ticketIds = new ArrayList<>();
                Matcher matcher = Pattern.compile("[0-9a-fA-F-]{36}").matcher(body.getOrDefault("ticketIds", ""));
                while (matcher.find()) ticketIds.add(uuid(matcher.group()));
                var result = operations.request(account, uuid(body.get("orderId")), ticketIds, body.get("reason"));
                HttpResponses.data(response, "{\"requestId\":" + HttpResponses.jsonString(result.requestId().toString())
                        + ",\"requestedAmount\":" + HttpResponses.jsonString(result.requestedAmount().toString()) + "}");
                return;
            }
            if (servlet.equals("/api/admin/refund-requests") && path.endsWith("/decision")) {
                String decision = operations.decide(account, pathId(path, "/decision"), body.get("decision"), body.get("rejectionReason"));
                HttpResponses.data(response, "{\"decision\":" + HttpResponses.jsonString(decision) + "}");
                return;
            }
            HttpResponses.error(response, 404, "NOT_FOUND", "Endpoint không tồn tại");
        } catch (SecurityException exception) {
            HttpResponses.error(response, 403, "FORBIDDEN", exception.getMessage());
        } catch (RuntimeException exception) {
            error(response, exception);
        }
    }

    private OperationsService resolve(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (service != null) return service;
        Object configured = request.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
        if (configured instanceof PersistenceRegistry registry) {
            return new OperationsService(registry.transactionManager(), new OperationsRepository());
        }
        HttpResponses.error(response, 503, "DEPENDENCY_UNAVAILABLE", "Operations service unavailable");
        return null;
    }

    private RefundService refundService(HttpServletRequest request) {
        if (configuredRefundService != null) return configuredRefundService;
        Object configured = request.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
        if (configured instanceof PersistenceRegistry registry) {
            return new RefundService(registry.transactionManager(), new RefundRepository());
        }
        throw new IllegalStateException("Refund service unavailable");
    }

    private static AuthenticatedAccount account(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object configured = request.getAttribute(AuthenticationFilter.ACCOUNT_ATTRIBUTE);
        if (configured instanceof AuthenticatedAccount account) return account;
        HttpResponses.error(response, 401, "UNAUTHORIZED", "Yêu cầu đăng nhập");
        return null;
    }

    private static UUID pathId(String path, String suffix) {
        if (path == null || !path.endsWith(suffix)) throw new IllegalArgumentException("path không hợp lệ");
        return uuid(path.substring(1, path.length() - suffix.length()));
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("id không hợp lệ");
        }
    }

    private static void error(HttpServletResponse response, RuntimeException exception) throws IOException {
        HttpResponses.error(response, exception instanceof NoSuchElementException ? 404 : 409,
                "REQUEST_FAILED", exception.getMessage());
    }
}
