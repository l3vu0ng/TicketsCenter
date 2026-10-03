package vn.ticketscenter.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("E2E Test: Authentication, CSRF, Session & Profile Lifecycle")
class AuthLifecycleE2EIT {

    private E2ETestClient client;

    @BeforeEach
    void setUp() {
        client = new E2ETestClient();
    }

    @Test
    @DisplayName("Health live and ready endpoints return UP status")
    void healthChecks_shouldReturnUp() throws Exception {
        E2ETestClient.Response live = client.get("/health/live");
        assertEquals(200, live.statusCode());
        assertTrue(live.body().contains("\"status\":\"UP\""));

        E2ETestClient.Response ready = client.get("/health/ready");
        assertEquals(200, ready.statusCode());
        assertTrue(ready.body().contains("\"status\":\"UP\""));
    }

    @Test
    @DisplayName("CSRF token is successfully issued and valid")
    void getCsrfToken_shouldReturnToken() throws Exception {
        E2ETestClient.Response response = client.get("/api/auth/csrf");
        assertEquals(200, response.statusCode());
        String token = response.extractString("token");
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    @DisplayName("Accessing protected profile without session returns 401")
    void profileWithoutAuth_shouldReturn401() throws Exception {
        E2ETestClient.Response response = client.get("/api/me/profile");
        assertEquals(401, response.statusCode());
    }

    @Test
    @DisplayName("Registration requires valid CSRF and password length >= 12")
    void registrationValidation_shouldRejectInvalidInputs() throws Exception {
        String testEmail = "test_" + UUID.randomUUID().toString().substring(0, 8) + "@ticketscenter.vn";

        // Without CSRF token -> 403 Forbidden
        E2ETestClient.Response noCsrf = client.post("/api/auth/register",
                "{\"email\":\"" + testEmail + "\",\"password\":\"ValidPass123456!\"}", false);
        assertEquals(403, noCsrf.statusCode());
        assertTrue(noCsrf.body().contains("CSRF_INVALID"));

        // With CSRF but password too short (< 12) -> 400 Bad Request
        E2ETestClient.Response shortPass = client.post("/api/auth/register",
                "{\"email\":\"" + testEmail + "\",\"password\":\"Short1!\"}", true);
        assertEquals(400, shortPass.statusCode());
        assertTrue(shortPass.body().contains("VALIDATION_FAILED"));

        // Valid registration -> 200 Accepted
        E2ETestClient.Response valid = client.post("/api/auth/register",
                "{\"email\":\"" + testEmail + "\",\"password\":\"ValidPass123456!\"}", true);
        assertEquals(200, valid.statusCode());
        assertTrue(valid.body().contains("accepted"));
    }

    @Test
    @DisplayName("Login with invalid credentials returns 401, valid returns session and profile")
    void loginAndProfileLifecycle_shouldAuthenticateSuccessfully() throws Exception {
        // Bad credentials
        E2ETestClient.Response badLogin = client.login("adminTIcket@gmail.com", "WrongPassword123!");
        assertEquals(401, badLogin.statusCode());
        assertTrue(badLogin.body().contains("INVALID_CREDENTIALS"));

        // Valid admin login
        E2ETestClient.Response goodLogin = client.login("adminTIcket@gmail.com", "123123ticketCenter");
        assertEquals(200, goodLogin.statusCode());
        assertTrue(goodLogin.body().contains("authenticated"));

        // Retrieve profile with authenticated session
        E2ETestClient.Response profile = client.get("/api/me/profile");
        assertEquals(200, profile.statusCode());
        assertEquals("adminTIcket@gmail.com", profile.extractString("email"));
        assertTrue(profile.body().contains("ADMIN"));

        // Logout
        E2ETestClient.Response logout = client.logout();
        assertEquals(200, logout.statusCode());

        // Profile after logout should return 401
        E2ETestClient.Response afterLogout = client.get("/api/me/profile");
        assertEquals(401, afterLogout.statusCode());
    }
}
