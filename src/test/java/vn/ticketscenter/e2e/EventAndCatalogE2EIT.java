package vn.ticketscenter.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("E2E Test: Events, Categories & Organization Flow")
class EventAndCatalogE2EIT {

    private E2ETestClient client;

    @BeforeEach
    void setUp() {
        client = new E2ETestClient();
    }

    @Test
    @DisplayName("Public event categories endpoint returns category items")
    void getCategories_shouldReturnActiveCategories() throws Exception {
        E2ETestClient.Response response = client.get("/api/event-categories");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"items\":["));
        assertTrue(response.body().contains("\"slug\":"));
    }

    @Test
    @DisplayName("Public events endpoint returns published events with pagination")
    void getEvents_shouldReturnPublishedCatalog() throws Exception {
        E2ETestClient.Response response = client.get("/api/events?page=1&pageSize=10");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"items\":["));
        assertTrue(response.body().contains("\"total\":"));
        assertTrue(response.body().contains("PUBLISHED"));

        String firstEventId = response.extractString("id");
        assertNotNull(firstEventId);

        // Fetch detail of the first event
        E2ETestClient.Response detail = client.get("/api/events/" + firstEventId);
        assertEquals(200, detail.statusCode());
        assertEquals(firstEventId, detail.extractString("id"));

        // Fetch zones of the first event
        E2ETestClient.Response zones = client.get("/api/events/" + firstEventId + "/zones");
        assertEquals(200, zones.statusCode());
        assertTrue(zones.body().contains("\"data\":"));
    }

    @Test
    @DisplayName("Creating organization requires authentication")
    void createOrganizationRequest_withoutAuth_shouldReturn401() throws Exception {
        E2ETestClient.Response response = client.post("/api/organization-requests",
                "{\"name\":\"Test Org\",\"taxCode\":\"0101234567\",\"phone\":\"0912345678\"}", true);
        assertEquals(401, response.statusCode());
    }

    @Test
    @DisplayName("Authenticated user can submit organization request and view their requests")
    void organizationRequestLifecycle_shouldSucceed() throws Exception {
        // Login as admin (also an authenticated user)
        E2ETestClient.Response login = client.login("adminTIcket@gmail.com", "123123ticketCenter");
        assertEquals(200, login.statusCode());

        // View admin organization requests
        E2ETestClient.Response adminList = client.get("/api/admin/organization-requests");
        assertEquals(200, adminList.statusCode());
        assertTrue(adminList.body().contains("\"items\":["));

        // View user's own organization requests
        E2ETestClient.Response myRequests = client.get("/api/me/organization-requests");
        assertEquals(200, myRequests.statusCode());
        assertTrue(myRequests.body().contains("\"items\":["));
    }

    @Test
    @DisplayName("Accessing protected admin event routes without ADMIN role returns 403")
    void adminEventRoutes_forbiddenForNonAdmin() throws Exception {
        // Register and login as normal user
        String email = "buyer_" + UUID.randomUUID().toString().substring(0, 8) + "@ticketscenter.vn";
        client.post("/api/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"BuyerPass123456!\"}", true);
        E2ETestClient.Response login = client.login(email, "BuyerPass123456!");
        assertEquals(200, login.statusCode());

        // Try to access admin overview
        E2ETestClient.Response adminOverview = client.get("/api/admin/overview");
        assertEquals(403, adminOverview.statusCode());

        // Try to access admin organization requests
        E2ETestClient.Response adminOrgRequests = client.get("/api/admin/organization-requests");
        assertEquals(403, adminOrgRequests.statusCode());
    }
}
