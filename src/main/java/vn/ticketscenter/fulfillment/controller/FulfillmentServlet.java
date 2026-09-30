package vn.ticketscenter.fulfillment.controller;

import jakarta.persistence.PersistenceException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.fulfillment.dto.FulfillmentDtos.TicketView;
import vn.ticketscenter.fulfillment.service.FulfillmentService;
import vn.ticketscenter.fulfillment.repository.FulfillmentRepository;
import vn.ticketscenter.identity.filter.AuthenticationFilter;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@WebServlet(name = "FulfillmentServlet", urlPatterns = {"/api/payments/*", "/api/me/tickets", "/api/tickets/*"})
public final class FulfillmentServlet extends HttpServlet {
    private final FulfillmentService service;
    public FulfillmentServlet() { service = null; }
    FulfillmentServlet(FulfillmentService service) { this.service = service; }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthenticatedAccount account = account(request, response); if (account == null) return;
        FulfillmentService resolved = resolve(request, response); if (resolved == null) return;
        try {
            String path = request.getPathInfo();
            if (request.getServletPath().equals("/api/me/tickets")) { HttpResponses.data(response, tickets(resolved.mine(account))); return; }
            if (path != null && path.endsWith("/qr")) {
                byte[] png = resolved.qr(account, uuid(path.substring(1, path.length() - 3)));
                response.setStatus(200); response.setContentType("image/png"); response.getOutputStream().write(png); return;
            }
            HttpResponses.error(response, 404, "NOT_FOUND", "Ticket endpoint not found");
        } catch (RuntimeException exception) { handle(response, exception); }
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthenticatedAccount account = account(request, response); if (account == null) return;
        FulfillmentService resolved = resolve(request, response); if (resolved == null) return;
        try {
            String path = request.getPathInfo();
            if (path == null || !path.startsWith("/")) throw new IllegalArgumentException("orderId is required");
            Map<String,String> body = JsonObjectParser.parse(request.getReader());
            UUID paymentId = body.get("paymentId") == null || body.get("paymentId").isBlank() ? null : uuid(body.get("paymentId"));
            var result = resolved.confirm(account, uuid(path.substring(1)), paymentId, body.get("providerReference"));
            HttpResponses.data(response, "{\"orderStatus\":" + HttpResponses.jsonString(result.orderStatus()) + ",\"paymentStatus\":" + HttpResponses.jsonString(result.paymentStatus()) + ",\"tickets\":" + tickets(result.tickets()) + "}");
        } catch (SecurityException exception) { HttpResponses.error(response, 403, "FORBIDDEN", exception.getMessage()); }
        catch (IllegalArgumentException exception) { HttpResponses.error(response, 400, "VALIDATION_FAILED", exception.getMessage()); }
        catch (PersistenceException | IllegalStateException exception) { HttpResponses.error(response, 409, "CONFLICT", exception.getMessage()); }
    }

    private FulfillmentService resolve(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (service != null) return service;
        Object configured = request.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
        if (configured instanceof PersistenceRegistry registry) return new FulfillmentService(registry.transactionManager(), new FulfillmentRepository());
        HttpResponses.error(response, 503, "DEPENDENCY_UNAVAILABLE", "Fulfillment service unavailable"); return null;
    }
    private static AuthenticatedAccount account(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object value = request.getAttribute(AuthenticationFilter.ACCOUNT_ATTRIBUTE);
        if (value instanceof AuthenticatedAccount account) return account;
        HttpResponses.error(response, 401, "UNAUTHORIZED", "Yêu cầu đăng nhập"); return null;
    }
    private static UUID uuid(String value) { try { return UUID.fromString(value); } catch (RuntimeException e) { throw new IllegalArgumentException("id không hợp lệ"); } }
    private static String tickets(List<TicketView> values) { return values.stream().map(t -> "{\"ticketId\":" + HttpResponses.jsonString(t.ticketId().toString()) + ",\"eventId\":" + HttpResponses.jsonString(t.eventId().toString()) + ",\"eventTitle\":" + HttpResponses.jsonString(t.eventTitle()) + ",\"venueName\":" + HttpResponses.jsonString(t.venueName()) + ",\"venueAddress\":" + HttpResponses.jsonString(t.venueAddress()) + ",\"zoneName\":" + HttpResponses.jsonString(t.zoneName()) + ",\"seatLabel\":" + (t.seatLabel() == null ? "null" : HttpResponses.jsonString(t.seatLabel())) + ",\"paidAmount\":" + HttpResponses.jsonString(t.paidAmount().toString()) + ",\"status\":" + HttpResponses.jsonString(t.status()) + ",\"issuedAt\":" + HttpResponses.jsonString(t.issuedAt().toString()) + "}").reduce((a,b)->a+","+b).map(s->"["+s+"]").orElse("[]"); }
    private static void handle(HttpServletResponse response, RuntimeException e) throws IOException { HttpResponses.error(response, e instanceof java.util.NoSuchElementException ? 404 : 409, "REQUEST_FAILED", e.getMessage()); }
}
