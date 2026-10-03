package vn.ticketscenter.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("E2E Test: Operations, Check-in & Refund Requests")
class CheckInE2EIT {

    private E2ETestClient client;

    @BeforeEach
    void setUp() {
        client = new E2ETestClient();
    }

    @Test
    @DisplayName("Unauthenticated operations requests return 401")
    void checkInWithoutAuth_shouldReturn401() throws Exception {
        // Check-in scan execution without session
        E2ETestClient.Response checkInPost = client.post("/api/check-ins",
                "{\"eventId\":\"" + UUID.randomUUID() + "\",\"ticketCode\":\"TC-12345\"}", true);
        assertEquals(401, checkInPost.statusCode());

        // Refund request creation without session
        E2ETestClient.Response refundPost = client.post("/api/refund-requests",
                "{\"orderId\":\"" + UUID.randomUUID() + "\",\"ticketIds\":\"[]\",\"reason\":\"Customer request\"}", true);
        assertEquals(401, refundPost.statusCode());
    }

    @Test
    @DisplayName("Check-in scan with missing eventId or ticketCode returns 409 REQUEST_FAILED")
    void checkInMalformedPayload_shouldReturn409() throws Exception {
        // Login as admin
        E2ETestClient.Response login = client.login("adminTIcket@gmail.com", "123123ticketCenter");
        assertEquals(200, login.statusCode());

        // Missing or malformed eventId in body -> returns 409 REQUEST_FAILED
        E2ETestClient.Response postInvalid = client.post("/api/check-ins",
                "{\"invalidField\":\"not-valid\"}", true);
        assertEquals(409, postInvalid.statusCode());
        assertTrue(postInvalid.body().contains("REQUEST_FAILED"));
    }

    @Test
    @DisplayName("Check-in scan for non-existent event returns 404, 403 or 409")
    void checkInNonExistentEvent_shouldReturnError() throws Exception {
        client.login("adminTIcket@gmail.com", "123123ticketCenter");

        UUID nonExistentEventId = UUID.randomUUID();
        E2ETestClient.Response response = client.post("/api/check-ins",
                "{\"eventId\":\"" + nonExistentEventId + "\",\"ticketCode\":\"TC-INVALID\"}", true);
        assertTrue(response.statusCode() == 404 || response.statusCode() == 403 || response.statusCode() == 409);
    }
}
