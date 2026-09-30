package vn.ticketscenter.event.controller;

import jakarta.persistence.PersistenceException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.event.dto.EventDtos.CreateEventCommand;
import vn.ticketscenter.event.dto.EventDtos.EventPage;
import vn.ticketscenter.event.dto.EventDtos.EventSearch;
import vn.ticketscenter.event.dto.EventDtos.EventView;
import vn.ticketscenter.event.dto.EventDtos.SeatSummaryDto;
import vn.ticketscenter.event.dto.EventDtos.ZoneCommand;
import vn.ticketscenter.event.dto.EventDtos.ZoneView;
import vn.ticketscenter.event.integration.storage.LocalImageStorage;
import vn.ticketscenter.event.repository.EventRepository;
import vn.ticketscenter.event.service.EventService;
import vn.ticketscenter.identity.filter.AuthenticationFilter;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@WebServlet(name = "EventServlet", urlPatterns = {"/api/events", "/api/events/*", "/api/zones/*", "/api/event-categories"})
@MultipartConfig(maxFileSize = 5_242_880, maxRequestSize = 5_300_000)
public final class EventServlet extends HttpServlet {
    private final EventService eventService;
    private final LocalImageStorage imageStorage;

    public EventServlet() {
        this.eventService = null;
        this.imageStorage = new LocalImageStorage(Path.of(
                AppConfig.get(AppConfig.Keys.EVENT_IMAGE_DIRECTORY, "./data/event-images")));
    }

    EventServlet(EventService eventService, LocalImageStorage imageStorage) {
        this.eventService = eventService;
        this.imageStorage = imageStorage;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        EventService service = resolveService(request, response);
        if (service == null) return;
        try {
            String path = apiPath(request);
            if ("/events".equals(path)) {
                EventPage page = service.search(new EventSearch(text(request.getParameter("q")),
                        optionalUuid(request.getParameter("categoryId")), instant(request.getParameter("from")),
                        instant(request.getParameter("to")), text(request.getParameter("sort")),
                        integer(request.getParameter("page"), 1), integer(request.getParameter("pageSize"), 20)));
                HttpResponses.data(response, pageJson(page));
                return;
            }
            if ("/event-categories".equals(path)) {
                List<Object[]> categories = service.getCategories();
                String items = categories.stream().map(row -> "{\"id\":" + json(row[0])
                        + ",\"name\":" + json(row[1]) + ",\"slug\":" + json(row[2]) + "}")
                        .reduce((a, b) -> a + "," + b).orElse("");
                HttpResponses.data(response, "{\"items\":[" + items + "]}");
                return;
            }
            String[] parts = parts(path);
            if (parts.length == 2 && "events".equals(parts[0])) {
                HttpResponses.data(response, eventJson(service.getPublic(uuid(parts[1]))));
                return;
            }
            if (parts.length == 3 && "events".equals(parts[0]) && "zones".equals(parts[2])) {
                HttpResponses.data(response, zonesJson(service.getPublicZones(uuid(parts[1]))));
                return;
            }
            HttpResponses.error(response, 404, "NOT_FOUND", "Endpoint sự kiện không tồn tại");
        } catch (RuntimeException exception) {
            handle(response, exception);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        AuthenticatedAccount account = account(request, response);
        if (account == null) return;
        EventService service = resolveService(request, response);
        if (service == null) return;
        try {
            String path = apiPath(request);
            String[] parts = parts(path);
            if (parts.length == 3 && "events".equals(parts[0]) && "cover".equals(parts[2])) {
                Part cover;
                try {
                    cover = request.getPart("cover");
                } catch (IllegalStateException exception) {
                    HttpResponses.error(response, 413, "PAYLOAD_TOO_LARGE", "Ảnh bìa vượt giới hạn 5 MB");
                    return;
                }
                if (cover == null) throw new IllegalArgumentException("cover part is required");
                String url;
                try (var input = cover.getInputStream()) {
                    url = service.uploadCover(account, uuid(parts[1]), input, cover.getSubmittedFileName(), imageStorage);
                } catch (IOException exception) {
                    HttpResponses.error(response, 503, "STORAGE_UNAVAILABLE", "Không thể lưu ảnh bìa lúc này");
                    return;
                }
                HttpResponses.data(response, "{\"coverImageUrl\":" + json(url) + "}");
                return;
            }
            Map<String, String> body = JsonObjectParser.parse(request.getReader());
            if (parts.length == 3 && "events".equals(parts[0])) {
                UUID eventId = uuid(parts[1]);
                switch (parts[2]) {
                    case "edit" -> service.updateDraft(account, eventId, eventCommand(body));
                    case "zones" -> {
                        UUID zoneId = service.addZone(account, eventId, zoneCommand(body));
                        HttpResponses.data(response, "{\"zoneId\":" + json(zoneId) + "}");
                        return;
                    }
                    case "submit" -> service.submit(account, eventId);
                    case "delete" -> service.deleteDraft(account, eventId);
                    default -> {
                        HttpResponses.error(response, 404, "NOT_FOUND", "Thao tác sự kiện không tồn tại");
                        return;
                    }
                }
                HttpResponses.data(response, "{\"updated\":true}");
                return;
            }
            if (parts.length == 3 && "zones".equals(parts[0])) {
                UUID zoneId = uuid(parts[1]);
                if ("edit".equals(parts[2])) service.updateZone(account, zoneId, zoneCommand(body));
                else if ("delete".equals(parts[2])) service.deleteZone(account, zoneId);
                else {
                    HttpResponses.error(response, 404, "NOT_FOUND", "Thao tác khu không tồn tại");
                    return;
                }
                HttpResponses.data(response, "{\"updated\":true}");
                return;
            }
            HttpResponses.error(response, 404, "NOT_FOUND", "Endpoint sự kiện không tồn tại");
        } catch (RuntimeException exception) {
            handle(response, exception);
        }
    }

    public static CreateEventCommand eventCommand(Map<String, String> body) {
        return new CreateEventCommand(uuid(body.get("categoryId")), body.get("title"), body.get("description"),
                body.get("venueName"), body.get("venueAddress"), instantRequired(body.get("saleStart")),
                instantRequired(body.get("saleEnd")), instantRequired(body.get("startTime")),
                instantRequired(body.get("endTime")));
    }

    private static ZoneCommand zoneCommand(Map<String, String> body) {
        return new ZoneCommand(body.get("name"), body.get("type"), decimal(body.get("price")),
                optionalInteger(body.get("capacity")), optionalInteger(body.get("rows")),
                optionalInteger(body.get("seatsPerRow")));
    }

    private EventService resolveService(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (eventService != null) return eventService;
        Object configured = request.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
        if (configured instanceof PersistenceRegistry registry) {
            return new EventService(registry.transactionManager(), new EventRepository());
        }
        HttpResponses.error(response, 503, "DEPENDENCY_UNAVAILABLE", "Event service is temporarily unavailable");
        return null;
    }

    private AuthenticatedAccount account(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object value = request.getAttribute(AuthenticationFilter.ACCOUNT_ATTRIBUTE);
        if (value instanceof AuthenticatedAccount account) return account;
        HttpResponses.error(response, 401, "UNAUTHORIZED", "Yêu cầu đăng nhập để truy cập");
        return null;
    }

    public static String pageJson(EventPage page) {
        return "{\"items\":[" + page.items().stream().map(EventServlet::eventJson)
                .reduce((a, b) -> a + "," + b).orElse("") + "],\"page\":" + page.page()
                + ",\"pageSize\":" + page.pageSize() + ",\"total\":" + page.total() + "}";
    }

    private static String eventJson(EventView value) {
        return "{\"id\":" + json(value.id()) + ",\"organizationId\":" + json(value.organizationId())
                + ",\"categoryId\":" + json(value.categoryId()) + ",\"categoryName\":" + json(value.categoryName())
                + ",\"title\":" + json(value.title()) + ",\"description\":" + json(value.description())
                + ",\"coverImageUrl\":" + json(value.coverImageUrl()) + ",\"venueName\":" + json(value.venueName())
                + ",\"venueAddress\":" + json(value.venueAddress()) + ",\"saleStart\":" + json(value.saleStart())
                + ",\"saleEnd\":" + json(value.saleEnd()) + ",\"startTime\":" + json(value.startTime())
                + ",\"endTime\":" + json(value.endTime()) + ",\"status\":" + json(value.status())
                + ",\"rejectionReason\":" + json(value.rejectionReason())
                + ",\"minimumPrice\":" + json(value.minimumPrice()) + "}";
    }

    private static String zonesJson(List<ZoneView> zones) {
        return "{\"items\":[" + zones.stream().map(zone -> "{\"id\":" + json(zone.id())
                + ",\"name\":" + json(zone.name()) + ",\"type\":" + json(zone.type())
                + ",\"price\":" + json(zone.price()) + ",\"capacity\":" + zone.capacity()
                + ",\"heldQuantity\":" + zone.heldQuantity() + ",\"soldQuantity\":" + zone.soldQuantity()
                + ",\"availableQuantity\":" + zone.availableQuantity() + ",\"seats\":["
                + zone.seats().stream().map(seat -> "{\"id\":" + json(seat.id())
                        + ",\"row\":" + json(seat.rowCode()) + ",\"number\":" + json(seat.seatNumber())
                        + ",\"status\":" + json(seat.status()) + "}")
                        .reduce((a, b) -> a + "," + b).orElse("") + "]}")
                .reduce((a, b) -> a + "," + b).orElse("") + "]}";
    }

    private static void handle(HttpServletResponse response, RuntimeException exception) throws IOException {
        if (exception instanceof SecurityException) HttpResponses.error(response, 403, "FORBIDDEN", exception.getMessage());
        else if (exception instanceof NoSuchElementException) HttpResponses.error(response, 404, "NOT_FOUND", exception.getMessage());
        else if (exception instanceof IllegalArgumentException) HttpResponses.error(response, 400, "VALIDATION_FAILED", exception.getMessage());
        else if (exception instanceof IllegalStateException || exception instanceof PersistenceException)
            HttpResponses.error(response, 409, "CONFLICT", exception.getMessage());
        else throw exception;
    }

    private static String apiPath(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/api") ? path.substring(4) : path;
    }

    private static String[] parts(String path) {
        return Arrays.stream(path.split("/")).filter(part -> !part.isBlank()).toArray(String[]::new);
    }

    private static UUID uuid(String value) {
        try { return UUID.fromString(value); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("id không hợp lệ"); }
    }

    private static UUID optionalUuid(String value) { return text(value) == null ? null : uuid(value); }
    private static Instant instant(String value) { return text(value) == null ? null : instantRequired(value); }
    private static Instant instantRequired(String value) {
        try { return Instant.parse(value); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("thời điểm không hợp lệ"); }
    }
    private static BigDecimal decimal(String value) {
        try { return new BigDecimal(value); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("price không hợp lệ"); }
    }
    private static Integer optionalInteger(String value) {
        if (text(value) == null) return null;
        try { return Integer.valueOf(value); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("số lượng không hợp lệ"); }
    }
    private static int integer(String value, int fallback) { return optionalInteger(value) == null ? fallback : optionalInteger(value); }
    private static String text(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String json(Object value) { return value == null ? "null" : HttpResponses.jsonString(value.toString()); }
}
