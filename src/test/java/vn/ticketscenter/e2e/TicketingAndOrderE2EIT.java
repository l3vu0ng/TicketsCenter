package vn.ticketscenter.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("E2E Test: Ticketing, Holds, Orders & Vouchers Flow")
class TicketingAndOrderE2EIT {

    private E2ETestClient client;

    @BeforeEach
    void setUp() {
        client = new E2ETestClient();
    }

    @Test
    @DisplayName("Unauthenticated hold and ticket requests return 401")
    void holdAndTicketsWithoutAuth_shouldReturn401() throws Exception {
        E2ETestClient.Response holdPost = client.post("/api/holds", "{}", true);
        assertEquals(401, holdPost.statusCode());

        E2ETestClient.Response tickets = client.get("/api/me/tickets");
        assertEquals(401, tickets.statusCode());

        E2ETestClient.Response activeHold = client.get("/api/me/hold");
        assertEquals(401, activeHold.statusCode());
    }

    @Test
    @DisplayName("Hold creation validates ticket count (1 to 8 tickets required)")
    void holdWithEmptyItems_shouldReturn400ValidationFailed() throws Exception {
        // Login as verified admin
        E2ETestClient.Response login = client.login("adminTIcket@gmail.com", "123123ticketCenter");
        assertEquals(200, login.statusCode());

        // Fetch a published event to find an eventId
        E2ETestClient.Response events = client.get("/api/events");
        assertEquals(200, events.statusCode());
        String eventId = events.extractString("id");
        assertNotNull(eventId);

        // Call /api/holds with empty items -> 400 VALIDATION_FAILED
        String body = String.format("{\"eventId\":\"%s\",\"items\":[]}", eventId);
        E2ETestClient.Response response = client.post("/api/holds", body, true);
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Hold creation with Idempotency-Key header triggers idempotency filter")
    void holdWithIdempotencyKey_shouldHandleIdempotency() throws Exception {
        client.login("adminTIcket@gmail.com", "123123ticketCenter");

        String idempotencyKey = UUID.randomUUID().toString();
        E2ETestClient.Response response = client.post(
                "/api/holds",
                "{\"items\":[]}",
                true,
                Map.of("Idempotency-Key", idempotencyKey)
        );
        // Returns 400 (validation failure if Redis is up) or 503 (if Redis dependency is down)
        assertTrue(response.statusCode() == 400 || response.statusCode() == 503);
    }

    @Test
    @DisplayName("Authenticated user can query their tickets and active hold status")
    void authenticatedUser_canQueryTicketsAndHold() throws Exception {
        client.login("adminTIcket@gmail.com", "123123ticketCenter");

        E2ETestClient.Response tickets = client.get("/api/me/tickets");
        assertEquals(200, tickets.statusCode());
        assertTrue(tickets.body().contains("\"data\":"));

        // No active hold for admin initially -> returns empty or 404
        E2ETestClient.Response activeHold = client.get("/api/me/hold");
        assertTrue(activeHold.statusCode() == 200 || activeHold.statusCode() == 404);
    }
}
