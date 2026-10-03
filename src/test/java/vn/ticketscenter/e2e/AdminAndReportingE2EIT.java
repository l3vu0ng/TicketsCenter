package vn.ticketscenter.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("E2E Test: Admin Operations, Audit Logs & Reporting Flow")
class AdminAndReportingE2EIT {

    private E2ETestClient client;

    @BeforeEach
    void setUp() {
        client = new E2ETestClient();
    }

    @Test
    @DisplayName("Admin endpoints reject unauthenticated access with 401")
    void adminEndpoints_unauthenticated_shouldReturn401() throws Exception {
        E2ETestClient.Response overview = client.get("/api/admin/overview");
        assertEquals(401, overview.statusCode());

        E2ETestClient.Response audit = client.get("/api/admin/audit-logs");
        assertEquals(401, audit.statusCode());

        E2ETestClient.Response report = client.get("/api/admin/reports");
        assertEquals(401, report.statusCode());

        E2ETestClient.Response export = client.get("/api/reports/export");
        assertEquals(401, export.statusCode());
    }

    @Test
    @DisplayName("Admin endpoints reject non-admin users with 403")
    void adminEndpoints_nonAdmin_shouldReturn403() throws Exception {
        String email = "regular_" + UUID.randomUUID().toString().substring(0, 8) + "@ticketscenter.vn";
        client.post("/api/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"RegularPass123456!\"}", true);
        client.login(email, "RegularPass123456!");

        E2ETestClient.Response overview = client.get("/api/admin/overview");
        assertEquals(403, overview.statusCode());

        E2ETestClient.Response audit = client.get("/api/admin/audit-logs");
        assertEquals(403, audit.statusCode());

        E2ETestClient.Response report = client.get("/api/admin/reports");
        assertEquals(403, report.statusCode());
    }

    @Test
    @DisplayName("Admin overview returns valid pending work metrics")
    void adminOverview_shouldReturnMetrics() throws Exception {
        client.login("adminTIcket@gmail.com", "123123ticketCenter");

        E2ETestClient.Response overview = client.get("/api/admin/overview");
        assertEquals(200, overview.statusCode());
        assertTrue(overview.body().contains("\"pendingOrganizationRequests\":"));
        assertTrue(overview.body().contains("\"pendingEvents\":"));
        assertTrue(overview.body().contains("\"pendingRefundRequests\":"));
        assertTrue(overview.body().contains("\"pendingSettlements\":"));
    }

    @Test
    @DisplayName("Admin audit logs return paginated append-only records")
    void adminAuditLogs_shouldReturnPaginatedEntries() throws Exception {
        client.login("adminTIcket@gmail.com", "123123ticketCenter");

        E2ETestClient.Response audit = client.get("/api/admin/audit-logs?page=1&pageSize=10");
        assertEquals(200, audit.statusCode());
        assertTrue(audit.body().contains("\"items\":["));
        assertTrue(audit.body().contains("\"total\":"));
        assertTrue(audit.body().contains("\"action\":"));
        assertTrue(audit.body().contains("\"aggregateType\":"));
    }

    @Test
    @DisplayName("Admin can export system report to CSV")
    void adminReportExport_shouldGenerateCsv() throws Exception {
        client.login("adminTIcket@gmail.com", "123123ticketCenter");

        E2ETestClient.Response export = client.get("/api/reports/export?from=2026-09-01T00:00:00Z&to=2026-10-04T00:00:00Z");
        assertEquals(200, export.statusCode());
        assertTrue(export.body().contains("metric,value"));
        assertTrue(export.body().contains("eventId,organizationId,eventTitle"));
    }
}
