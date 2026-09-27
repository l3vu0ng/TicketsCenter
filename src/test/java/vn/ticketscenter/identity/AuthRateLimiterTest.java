package vn.ticketscenter.identity;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.PersistenceListener;
import vn.ticketscenter.config.PersistenceRegistry;
import vn.ticketscenter.controller.identity.AuthServlet;
import vn.ticketscenter.service.identity.AuthRateLimiter;
import vn.ticketscenter.transaction.DatabasePrincipal;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Authentication & Registration Rate Limiter Tests")
class AuthRateLimiterTest {

    @Test
    void loginRateLimitExceededAfterMaxAccountAttempts() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T10:00:00Z"));
        AuthRateLimiter limiter = new AuthRateLimiter(clock);
        String email = "victim@example.com";
        String ip = "192.168.1.100";

        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> limiter.checkLogin(email, ip));
            limiter.recordLoginFailure(email, ip);
        }

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> limiter.checkLogin(email, ip));
        assertTrue(ex.getMessage().contains("Account login rate limit exceeded"));
    }

    @Test
    void loginRateLimitExceededAfterMaxIpAttemptsAcrossDifferentAccounts() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T10:00:00Z"));
        AuthRateLimiter limiter = new AuthRateLimiter(clock);
        String ip = "10.0.0.50";

        for (int i = 0; i < 20; i++) {
            String email = "target" + i + "@example.com";
            assertDoesNotThrow(() -> limiter.checkLogin(email, ip));
            limiter.recordLoginFailure(email, ip);
        }

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> limiter.checkLogin("fresh@example.com", ip));
        assertTrue(ex.getMessage().contains("IP login rate limit exceeded"));
    }

    @Test
    void loginSuccessResetsAccountFailureCount() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T10:00:00Z"));
        AuthRateLimiter limiter = new AuthRateLimiter(clock);
        String email = "user@example.com";
        String ip = "192.168.1.5";

        for (int i = 0; i < 4; i++) {
            limiter.recordLoginFailure(email, ip);
        }

        limiter.recordLoginSuccess(email, ip);

        for (int i = 0; i < 4; i++) {
            assertDoesNotThrow(() -> limiter.checkLogin(email, ip));
            limiter.recordLoginFailure(email, ip);
        }
    }

    @Test
    void registerRateLimitExceededAfterMaxIpAttempts() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T10:00:00Z"));
        AuthRateLimiter limiter = new AuthRateLimiter(clock);
        String ip = "172.16.0.10";

        for (int i = 0; i < 10; i++) {
            String email = "spammer" + i + "@example.com";
            assertDoesNotThrow(() -> limiter.checkRegister(email, ip));
            limiter.recordRegister(email, ip);
        }

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> limiter.checkRegister("spammer11@example.com", ip));
        assertTrue(ex.getMessage().contains("IP registration rate limit exceeded"));
    }

    @Test
    void rateLimitExpiresAfterWindowDuration() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T10:00:00Z"));
        AuthRateLimiter limiter = new AuthRateLimiter(clock);
        String email = "timeout@example.com";
        String ip = "192.168.1.20";

        for (int i = 0; i < 5; i++) {
            limiter.recordLoginFailure(email, ip);
        }
        assertThrows(IllegalStateException.class, () -> limiter.checkLogin(email, ip));

        clock.advance(Duration.ofMinutes(16));

        assertDoesNotThrow(() -> limiter.checkLogin(email, ip));
    }

    @Test
    void servletReturns429WhenLoginRateLimitTrips() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T10:00:00Z"));
        AuthRateLimiter limiter = new AuthRateLimiter(clock);
        AuthServlet servlet = new AuthServlet();

        for (int i = 0; i < 5; i++) {
            limiter.recordLoginFailure("blocked@example.com", "127.0.0.1");
        }

        final int[] status = new int[]{200};
        final StringWriter writer = new StringWriter();

        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setStatus" -> { status[0] = (int) args[0]; yield null; }
                    case "setHeader", "setContentType", "setCharacterEncoding" -> null;
                    case "getWriter" -> new PrintWriter(writer);
                    default -> null;
                });

        PersistenceRegistry registry = createDummyRegistry();
        ServletContext servletContext = (ServletContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ServletContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAttribute" -> {
                        if (PersistenceListener.REGISTRY_ATTRIBUTE.equals(args[0])) yield registry;
                        if (AuthServlet.RATE_LIMITER_ATTRIBUTE.equals(args[0])) yield limiter;
                        yield null;
                    }
                    default -> null;
                });

        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMethod" -> "POST";
                    case "getRequestURI" -> "/api/auth/login";
                    case "getContentType" -> "application/json";
                    case "getRemoteAddr" -> "127.0.0.1";
                    case "getServletContext" -> servletContext;
                    case "getReader" -> new BufferedReader(new StringReader("{\"email\":\"blocked@example.com\",\"password\":\"SomePassword123\"}"));
                    default -> null;
                });

        servlet.service(request, response);

        assertEquals(429, status[0]);
        assertTrue(writer.toString().contains("RATE_LIMITED"));
        assertTrue(writer.toString().contains("Account login rate limit exceeded"));
    }

    @Test
    void servletReturns429WhenRegisterRateLimitTrips() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T10:00:00Z"));
        AuthRateLimiter limiter = new AuthRateLimiter(clock);
        AuthServlet servlet = new AuthServlet();

        for (int i = 0; i < 10; i++) {
            limiter.recordRegister("user" + i + "@example.com", "127.0.0.1");
        }

        final int[] status = new int[]{200};
        final StringWriter writer = new StringWriter();

        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setStatus" -> { status[0] = (int) args[0]; yield null; }
                    case "setHeader", "setContentType", "setCharacterEncoding" -> null;
                    case "getWriter" -> new PrintWriter(writer);
                    default -> null;
                });

        PersistenceRegistry registry = createDummyRegistry();
        ServletContext servletContext = (ServletContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ServletContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAttribute" -> {
                        if (PersistenceListener.REGISTRY_ATTRIBUTE.equals(args[0])) yield registry;
                        if (AuthServlet.RATE_LIMITER_ATTRIBUTE.equals(args[0])) yield limiter;
                        yield null;
                    }
                    default -> null;
                });

        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMethod" -> "POST";
                    case "getRequestURI" -> "/api/auth/register";
                    case "getContentType" -> "application/json";
                    case "getRemoteAddr" -> "127.0.0.1";
                    case "getServletContext" -> servletContext;
                    case "getReader" -> new BufferedReader(new StringReader("{\"email\":\"fresh@example.com\",\"password\":\"SomePassword123\"}"));
                    default -> null;
                });

        servlet.service(request, response);

        assertEquals(429, status[0]);
        assertTrue(writer.toString().contains("RATE_LIMITED"));
        assertTrue(writer.toString().contains("IP registration rate limit exceeded"));
    }

    private PersistenceRegistry createDummyRegistry() throws Exception {
        PersistenceRegistry registry = new PersistenceRegistry();
        Field field = PersistenceRegistry.class.getDeclaredField("factories");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<DatabasePrincipal, jakarta.persistence.EntityManagerFactory> map =
                (Map<DatabasePrincipal, jakarta.persistence.EntityManagerFactory>) field.get(registry);
        jakarta.persistence.EntityManagerFactory factory = (jakarta.persistence.EntityManagerFactory) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{jakarta.persistence.EntityManagerFactory.class},
                (proxy, method, args) -> null);
        map.put(DatabasePrincipal.AUTH, factory);
        return registry;
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        MutableClock(Instant initial) {
            this.current = initial;
        }

        void advance(Duration duration) {
            this.current = this.current.plus(duration);
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
    }
}
