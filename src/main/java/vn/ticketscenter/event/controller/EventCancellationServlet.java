package vn.ticketscenter.event.controller;

import jakarta.persistence.PersistenceException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.event.dto.EventCancellationDtos.CancellationException;
import vn.ticketscenter.event.dto.EventCancellationDtos.CancellationProgress;
import vn.ticketscenter.event.repository.EventCancellationRepository;
import vn.ticketscenter.event.service.EventCancellationService;
import vn.ticketscenter.identity.filter.AuthenticationFilter;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;

import java.io.IOException;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.UUID;

@WebServlet(name = "EventCancellationServlet", urlPatterns = "/api/admin/events/*")
public final class EventCancellationServlet extends HttpServlet {
    private final EventCancellationService service;

    public EventCancellationServlet() {
        service = null;
    }

    EventCancellationServlet(EventCancellationService service) {
        this.service = service;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response, true);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response, false);
    }

    private void handle(HttpServletRequest request, HttpServletResponse response, boolean mutation) throws IOException {
        AuthenticatedAccount account = account(request, response);
        if (account == null) return;
        EventCancellationService cancellationService = resolveService(request, response);
        if (cancellationService == null) return;
        try {
            String[] parts = Arrays.stream(request.getPathInfo() == null ? new String[0]
                            : request.getPathInfo().split("/"))
                    .filter(part -> !part.isBlank()).toArray(String[]::new);
            if (parts.length != 2 || mutation != "cancel".equals(parts[1])
                    || !mutation && !"cancellation-progress".equals(parts[1])) {
                HttpResponses.error(response, 404, "NOT_FOUND", "Endpoint hủy sự kiện không tồn tại");
                return;
            }
            UUID eventId = uuid(parts[0]);
            CancellationProgress progress = mutation
                    ? cancellationService.cancel(account, eventId)
                    : cancellationService.progress(account, eventId);
            HttpResponses.data(response, json(progress));
        } catch (SecurityException exception) {
            HttpResponses.error(response, 403, "FORBIDDEN", "Quyền Quản trị viên (ADMIN) là bắt buộc");
        } catch (NoSuchElementException exception) {
            HttpResponses.error(response, 404, "NOT_FOUND", exception.getMessage());
        } catch (IllegalArgumentException exception) {
            HttpResponses.error(response, 400, "VALIDATION_FAILED", exception.getMessage());
        } catch (IllegalStateException | PersistenceException exception) {
            HttpResponses.error(response, 409, "CONFLICT", "Không thể xử lý hủy sự kiện");
        }
    }

    private EventCancellationService resolveService(HttpServletRequest request,
                                                     HttpServletResponse response) throws IOException {
        if (service != null) return service;
        Object configured = request.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
        if (configured instanceof PersistenceRegistry registry) {
            return new EventCancellationService(registry.transactionManager(), new EventCancellationRepository());
        }
        HttpResponses.error(response, 503, "DEPENDENCY_UNAVAILABLE",
                "Event cancellation service is temporarily unavailable");
        return null;
    }

    private static AuthenticatedAccount account(HttpServletRequest request,
                                                HttpServletResponse response) throws IOException {
        Object value = request.getAttribute(AuthenticationFilter.ACCOUNT_ATTRIBUTE);
        if (value instanceof AuthenticatedAccount account) return account;
        HttpResponses.error(response, 401, "UNAUTHORIZED", "Yêu cầu đăng nhập để truy cập");
        return null;
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("eventId không hợp lệ");
        }
    }

    private static String json(CancellationProgress value) {
        String exceptions = value.exceptions().stream().map(EventCancellationServlet::json)
                .reduce((left, right) -> left + "," + right).orElse("");
        return "{\"eventId\":" + string(value.eventId().toString())
                + ",\"status\":" + string(value.status())
                + ",\"totalOrders\":" + value.totalOrders()
                + ",\"pendingOrders\":" + value.pendingOrders()
                + ",\"completedOrders\":" + value.completedOrders()
                + ",\"exceptionCount\":" + value.exceptionCount()
                + ",\"requestsCreated\":" + value.requestsCreated()
                + ",\"refundsSucceeded\":" + value.refundsSucceeded()
                + ",\"refundsPending\":" + value.refundsPending()
                + ",\"exceptions\":[" + exceptions + "]}";
    }

    private static String json(CancellationException value) {
        return "{\"type\":" + string(value.type())
                + ",\"exceptionId\":" + string(value.exceptionId().toString())
                + ",\"orderId\":" + string(value.orderId().toString())
                + ",\"status\":" + string(value.status()) + "}";
    }

    private static String string(String value) {
        return HttpResponses.jsonString(value);
    }
}
