package vn.ticketscenter.order.controller;

import jakarta.persistence.PersistenceException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.identity.filter.AuthenticationFilter;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;
import vn.ticketscenter.order.dto.OrderDtos.OrderResult;
import vn.ticketscenter.fulfillment.dto.FulfillmentDtos.RefundableTicket;
import vn.ticketscenter.fulfillment.repository.OperationsRepository;
import vn.ticketscenter.fulfillment.service.OperationsService;
import vn.ticketscenter.order.repository.OrderRepository;
import vn.ticketscenter.order.service.OrderService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@WebServlet(name = "OrderServlet", urlPatterns = {"/api/orders", "/api/orders/*"})
public final class OrderServlet extends HttpServlet {
    private final OrderService orderService;

    public OrderServlet() { this.orderService = null; }
    OrderServlet(OrderService orderService) { this.orderService = orderService; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthenticatedAccount account = account(request, response);
        if (account == null) return;
        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo != null && pathInfo.endsWith("/refundable-tickets")) {
                String idPart = pathInfo.substring(1, pathInfo.length() - 19);
                UUID orderId = uuid(idPart);
                Object configured = request.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
                if (configured instanceof PersistenceRegistry registry) {
                    OperationsService service = new OperationsService(registry.transactionManager(), new OperationsRepository());
                    List<RefundableTicket> tickets = service.refundable(account, orderId);
                    HttpResponses.data(response, "{\"items\":" + refunds(tickets) + "}");
                    return;
                }
                HttpResponses.error(response, 503, "DEPENDENCY_UNAVAILABLE", "Operations service unavailable");
                return;
            }
            HttpResponses.error(response, 404, "NOT_FOUND", "Endpoint không tồn tại");
        } catch (SecurityException exception) {
            HttpResponses.error(response, 403, "FORBIDDEN", exception.getMessage());
        } catch (IllegalArgumentException exception) {
            HttpResponses.error(response, 400, "VALIDATION_FAILED", exception.getMessage());
        } catch (RuntimeException exception) {
            HttpResponses.error(response, exception instanceof NoSuchElementException ? 404 : 409, "REQUEST_FAILED", exception.getMessage());
        }
    }

    private static String refunds(List<RefundableTicket> v) {
        return v.stream().map(x -> "{\"ticketId\":" + HttpResponses.jsonString(x.ticketId().toString())
                + ",\"paidAmount\":" + HttpResponses.jsonString(x.paidAmount().toString())
                + ",\"zoneName\":" + HttpResponses.jsonString(x.zoneName())
                + ",\"seatLabel\":" + (x.seatLabel() == null ? "null" : HttpResponses.jsonString(x.seatLabel())) + "}")
                .reduce((a, b) -> a + "," + b)
                .map(x -> "[" + x + "]")
                .orElse("[]");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthenticatedAccount account = account(request, response);
        if (account == null) return;
        OrderService service = resolveService(request, response);
        if (service == null) return;
        try {
            String[] parts = request.getPathInfo() == null ? new String[0] : request.getPathInfo().split("/");
            Map<String, String> body = JsonObjectParser.parse(request.getReader());
            if (parts.length == 1) {
                OrderResult result = service.createFromHold(account, uuid(body.get("holdId")));
                HttpResponses.data(response, json(result));
                return;
            }
            if (parts.length == 3 && "coupon".equals(parts[2])) {
                OrderResult result = service.applyCoupon(account, uuid(parts[1]), body.get("couponCode"));
                HttpResponses.data(response, json(result));
                return;
            }
            HttpResponses.error(response, 404, "NOT_FOUND", "Endpoint đơn hàng không tồn tại");
        } catch (SecurityException exception) {
            HttpResponses.error(response, 403, "FORBIDDEN", exception.getMessage());
        } catch (IllegalArgumentException exception) {
            HttpResponses.error(response, 400, "VALIDATION_FAILED", exception.getMessage());
        } catch (PersistenceException | IllegalStateException exception) {
            HttpResponses.error(response, 409, "CONFLICT", exception.getMessage());
        }
    }

    private OrderService resolveService(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (orderService != null) return orderService;
        Object configured = request.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
        if (configured instanceof PersistenceRegistry registry) {
            return new OrderService(registry.transactionManager(), new OrderRepository());
        }
        HttpResponses.error(response, 503, "DEPENDENCY_UNAVAILABLE", "Order service is temporarily unavailable");
        return null;
    }

    private AuthenticatedAccount account(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object value = request.getAttribute(AuthenticationFilter.ACCOUNT_ATTRIBUTE);
        if (value instanceof AuthenticatedAccount account) return account;
        HttpResponses.error(response, 401, "UNAUTHORIZED", "Yêu cầu đăng nhập để truy cập");
        return null;
    }

    private static UUID uuid(String value) {
        try { return UUID.fromString(value); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("id không hợp lệ"); }
    }

    private static String json(OrderResult result) {
        return "{\"orderId\":" + jsonValue(result.orderId()) + ",\"orderCode\":" + jsonValue(result.orderCode())
                + ",\"subtotal\":" + jsonValue(result.subtotal()) + ",\"discountAmount\":" + jsonValue(result.discount())
                + ",\"total\":" + jsonValue(result.total()) + ",\"status\":" + jsonValue(result.status())
                + ",\"couponId\":" + jsonValue(result.couponId()) + "}";
    }

    private static String jsonValue(Object value) {
        return value == null ? "null" : HttpResponses.jsonString(value.toString());
    }
}
