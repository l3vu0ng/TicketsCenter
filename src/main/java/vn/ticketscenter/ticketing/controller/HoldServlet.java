package vn.ticketscenter.ticketing.controller;

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
import vn.ticketscenter.ticketing.dto.TicketingDtos.CreateHoldRequest;
import vn.ticketscenter.ticketing.dto.TicketingDtos.HoldItemRequest;
import vn.ticketscenter.ticketing.dto.TicketingDtos.HoldResponseDto;
import vn.ticketscenter.ticketing.repository.HoldRepository;
import vn.ticketscenter.ticketing.service.HoldService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet(name = "HoldServlet", urlPatterns = {"/api/holds", "/api/holds/*"})
public final class HoldServlet extends HttpServlet {
    private final HoldService holdService;

    public HoldServlet() { this.holdService = null; }
    HoldServlet(HoldService holdService) { this.holdService = holdService; }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object value = request.getAttribute(AuthenticationFilter.ACCOUNT_ATTRIBUTE);
        if (!(value instanceof AuthenticatedAccount account)) {
            HttpResponses.error(response, 401, "UNAUTHORIZED", "Yêu cầu đăng nhập để giữ vé");
            return;
        }
        HoldService service = resolveService(request, response);
        if (service == null) return;
        try {
            String path = request.getPathInfo();
            Map<String, String> body = JsonObjectParser.parse(request.getReader());
            HoldResponseDto result;
            if (path == null || "/".equals(path) || path.isBlank()) {
                result = service.create(account, request(body));
            } else if (path.startsWith("/") && path.endsWith("/cancel")) {
                result = service.cancel(account, uuid(path.substring(1, path.length() - "/cancel".length())));
            } else {
                HttpResponses.error(response, 404, "NOT_FOUND", "Endpoint giữ vé không tồn tại");
                return;
            }
            HttpResponses.data(response, json(result));
        } catch (SecurityException exception) {
            HttpResponses.error(response, 403, "FORBIDDEN", exception.getMessage());
        } catch (IllegalArgumentException exception) {
            HttpResponses.error(response, 400, "VALIDATION_FAILED", exception.getMessage());
        } catch (PersistenceException | IllegalStateException exception) {
            HttpResponses.error(response, 409, "CONFLICT", exception.getMessage());
        }
    }

    private HoldService resolveService(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (holdService != null) return holdService;
        Object configured = request.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
        if (configured instanceof PersistenceRegistry registry) return new HoldService(registry.transactionManager(), new HoldRepository());
        HttpResponses.error(response, 503, "DEPENDENCY_UNAVAILABLE", "Hold service is temporarily unavailable");
        return null;
    }

    private static CreateHoldRequest request(Map<String, String> body) {
        var items = new ArrayList<HoldItemRequest>();
        Matcher matcher = Pattern.compile("\\{\\s*\\\"zoneId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"seatId\\\"\\s*:\\s*(null|\\\"([^\\\"]+)\\\")\\s*,\\s*\\\"quantity\\\"\\s*:\\s*(-?\\d+)\\s*}").matcher(body.getOrDefault("items", ""));
        while (matcher.find()) items.add(new HoldItemRequest(uuid(matcher.group(1)), "null".equals(matcher.group(2)) ? null : uuid(matcher.group(3)), Integer.parseInt(matcher.group(4))));
        if (items.isEmpty() && !"[]".equals(body.get("items"))) throw new IllegalArgumentException("items is invalid");
        if (body.get("items") == null) throw new IllegalArgumentException("items is required");
        if (!body.get("items").trim().startsWith("[") || !body.get("items").trim().endsWith("]")) {
            throw new IllegalArgumentException("items is invalid");
        }
        return new CreateHoldRequest(uuid(body.get("eventId")), items);
    }

    private static UUID uuid(String value) {
        try { return UUID.fromString(value); } catch (RuntimeException exception) { throw new IllegalArgumentException("id không hợp lệ"); }
    }

    private static String json(HoldResponseDto result) {
        return "{\"holdId\":" + HttpResponses.jsonString(result.holdId().toString())
                + ",\"eventId\":" + (result.eventId() == null ? "null" : HttpResponses.jsonString(result.eventId().toString()))
                + ",\"status\":" + HttpResponses.jsonString(result.status().name())
                + ",\"expiresAt\":" + (result.expiresAt() == null ? "null" : HttpResponses.jsonString(result.expiresAt().toString()))
                + ",\"totalHoldAmount\":" + result.totalHoldAmount() + "}";
    }
}
