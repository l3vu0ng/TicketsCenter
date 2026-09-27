package vn.ticketscenter.acceptance;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.PersistenceListener;
import vn.ticketscenter.config.PersistenceRegistry;
import vn.ticketscenter.identity.controller.AuthServlet;
import vn.ticketscenter.identity.controller.OtpServlet;
import vn.ticketscenter.identity.filter.AuthenticationFilter;
import vn.ticketscenter.identity.filter.CsrfFilter;
import vn.ticketscenter.config.web.RequestValidationFilter;
import vn.ticketscenter.identity.integration.mail.ConfiguredMailGateway;
import vn.ticketscenter.identity.model.Otp;
import vn.ticketscenter.identity.model.OtpPurpose;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.service.AccountService;
import vn.ticketscenter.identity.service.OtpService;
import vn.ticketscenter.identity.service.PasswordHasher;
import vn.ticketscenter.identity.service.PasswordResetService;
import vn.ticketscenter.identity.service.SessionService;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Day 05 Auth & OTP Acceptance Tests")
public class Day05IT {

    private static final byte[] SECRET = "day-05-acceptance-test-secret-123".getBytes(StandardCharsets.UTF_8);

    @Test
    void completeRegistrationAndOtpVerificationFlow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T08:00:00Z"));
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        PasswordHasher hasher = new PasswordHasher();
        InMemoryPersistence persistence = new InMemoryPersistence();

        AccountService accounts = new AccountService(persistence.manager(), hasher, clock);
        OtpService otps = new OtpService(persistence.manager(), mail, clock, SECRET, () -> "123456");

        accounts.register("newuser@example.com", "SecurePassword1234");
        User user = persistence.findUser("newuser@example.com");
        assertNotNull(user);
        assertNull(user.getEmailVerifiedAt());

        otps.sendOtp(user.getId(), "newuser@example.com", OtpPurpose.VERIFY_EMAIL);
        assertEquals(1, mail.getDispatchedMessages().size());
        assertEquals("123456", mail.getDispatchedMessages().getFirst().otpCode());

        var verifyResult = otps.verifyOtp("newuser@example.com", "123456", OtpPurpose.VERIFY_EMAIL);
        assertTrue(verifyResult.successful());

        assertNotNull(user.getEmailVerifiedAt());
        assertEquals(clock.instant(), user.getEmailVerifiedAt());
    }

    @Test
    void clockControlledMatrixCoversCooldownExpiryAndAttemptExhaustion() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T08:00:00Z"));
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        InMemoryPersistence persistence = new InMemoryPersistence();

        OtpService otps = new OtpService(persistence.manager(), mail, clock, SECRET, () -> "654321");
        otps.sendOtp(UUID.randomUUID(), "testmatrix@example.com", OtpPurpose.VERIFY_EMAIL);

        clock.advance(Duration.ofSeconds(59));
        assertThrows(IllegalStateException.class,
                () -> otps.sendOtp(UUID.randomUUID(), "testmatrix@example.com", OtpPurpose.VERIFY_EMAIL));

        clock.advance(Duration.ofSeconds(1));
        assertDoesNotThrow(() -> otps.sendOtp(UUID.randomUUID(), "testmatrix@example.com", OtpPurpose.VERIFY_EMAIL));

        for (int attempt = 1; attempt <= 4; attempt++) {
            var fail = otps.verifyOtp("testmatrix@example.com", "000000", OtpPurpose.VERIFY_EMAIL);
            assertFalse(fail.successful());
            assertEquals("OTP_INVALID", fail.error());
        }

        var fifthFail = otps.verifyOtp("testmatrix@example.com", "000000", OtpPurpose.VERIFY_EMAIL);
        assertFalse(fifthFail.successful());

        var exhausted = otps.verifyOtp("testmatrix@example.com", "654321", OtpPurpose.VERIFY_EMAIL);
        assertFalse(exhausted.successful());

        clock.advance(Duration.ofSeconds(60));
        otps.sendOtp(UUID.randomUUID(), "testmatrix@example.com", OtpPurpose.VERIFY_EMAIL);
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        var expired = otps.verifyOtp("testmatrix@example.com", "654321", OtpPurpose.VERIFY_EMAIL);
        assertFalse(expired.successful());
        assertEquals("OTP_EXPIRED", expired.error());
    }

    @Test
    void forgotPasswordDoesNotLeakExistenceAndCompletesReset() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T08:00:00Z"));
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        PasswordHasher hasher = new PasswordHasher();
        InMemoryPersistence persistence = new InMemoryPersistence();

        UUID activeId = UUID.randomUUID();
        User existingUser = new User(activeId, "active@example.com", "active@example.com", hasher.hash("OldPassword1234"), "Active", clock.instant());
        persistence.users.put(activeId, existingUser);

        OtpService otps = new OtpService(persistence.manager(), mail, clock, SECRET, () -> "777888");
        PasswordResetService resets = new PasswordResetService(persistence.manager(), otps, hasher, clock, SECRET);

        assertDoesNotThrow(() -> resets.requestReset("nonexistent@example.com"));
        assertEquals(0, mail.getDispatchedMessages().size());

        assertDoesNotThrow(() -> resets.requestReset("active@example.com"));
        assertEquals(1, mail.getDispatchedMessages().size());

        var tokenRes = resets.verifyResetOtp("active@example.com", "777888");
        assertTrue(tokenRes.successful());

        resets.resetPassword(tokenRes.token(), "BrandNewPassword1234");
        assertTrue(hasher.matches("BrandNewPassword1234", existingUser.getPasswordHash()));
        assertEquals(1, existingUser.getAuthVersion());
    }

    @Test
    void rejectsOversizedRequestBodyAtBoundary() throws Exception {
        RequestValidationFilter filter = new RequestValidationFilter();

        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getContentLengthLong" -> 70 * 1024L;
                    case "getMethod" -> "POST";
                    default -> null;
                });

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

        filter.doFilter(request, response, (req, res) -> fail("Filter chain should not be called"));
        assertEquals(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, status[0]);
        assertTrue(writer.toString().contains("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void untrustedProxyDoesNotSpoofIpRateLimiter() {
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHeader" -> {
                        if ("X-Forwarded-For".equalsIgnoreCase((String) args[0])) {
                            yield "203.0.113.195, 10.0.0.1";
                        }
                        yield null;
                    }
                    case "getRemoteAddr" -> "192.168.1.50";
                    default -> null;
                });

        String untrustedResult = OtpServlet.resolveClientIp(request);
        assertEquals("192.168.1.50", untrustedResult);

        try {
            System.setProperty("app.proxy.trusted", "true");
            String trustedResult = OtpServlet.resolveClientIp(request);
            assertEquals("203.0.113.195", trustedResult);
        } finally {
            System.clearProperty("app.proxy.trusted");
        }
    }

    @Test
    void otpServletRejectsInvalidParametersWithBadRequest() throws Exception {
        InMemoryPersistence persistence = new InMemoryPersistence();
        PersistenceRegistry registry = createTestRegistry(persistence);
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T08:00:00Z"));

        OtpServlet servlet = new OtpServlet();

        List<String> invalidEndpointsAndBodies = List.of(
                "/api/auth/otp/send|{}",
                "/api/auth/otp/send|{\"purpose\":\"NON_EXISTENT_PURPOSE\"}",
                "/api/auth/otp/send|{\"purpose\":\"VERIFY_EMAIL\"}",
                "/api/auth/otp/verify|{\"email\":\"test@example.com\"}",
                "/api/auth/password/reset|{\"resetToken\":\"valid-format\"}"
        );

        for (String item : invalidEndpointsAndBodies) {
            String[] parts = item.split("\\|");
            String uri = parts[0];
            String jsonBody = parts[1];

            final int[] status = new int[]{200};
            final StringWriter writer = new StringWriter();

            ServletContext servletContext = (ServletContext) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{ServletContext.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getAttribute" -> {
                            if (PersistenceListener.REGISTRY_ATTRIBUTE.equals(args[0])) yield registry;
                            if (OtpServlet.MAIL_GATEWAY_ATTRIBUTE.equals(args[0])) yield mail;
                            if (OtpServlet.CLOCK_ATTRIBUTE.equals(args[0])) yield clock;
                            yield null;
                        }
                        default -> null;
                    });

            HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getMethod" -> "POST";
                        case "getRequestURI" -> uri;
                        case "getServletContext" -> servletContext;
                        case "getContentType" -> "application/json";
                        case "getReader" -> new BufferedReader(new StringReader(jsonBody));
                        case "getRemoteAddr" -> "127.0.0.1";
                        case "getAttribute" -> null;
                        default -> null;
                    });

            HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "setStatus" -> { status[0] = (int) args[0]; yield null; }
                        case "setHeader", "setContentType", "setCharacterEncoding" -> null;
                        case "getWriter" -> new PrintWriter(writer);
                        default -> null;
                    });

            servlet.service(request, response);
            assertEquals(HttpServletResponse.SC_BAD_REQUEST, status[0], "Failed on " + uri + " with " + jsonBody);
            assertTrue(writer.toString().contains("VALIDATION_FAILED") || writer.toString().contains("error"),
                    "Response did not contain error details: " + writer);
        }
    }

    @Test
    void authenticationFilterRevokesSessionWhenAuthVersionMismatched() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T08:00:00Z"));
        PasswordHasher hasher = new PasswordHasher();
        InMemoryPersistence persistence = new InMemoryPersistence();

        UUID userId = UUID.randomUUID();
        User user = new User(userId, "auth_check@example.com", "auth_check@example.com", hasher.hash("SecurePassword1234"), "AuthCheck", clock.instant());
        user.updatePassword(hasher.hash("BrandNewPassword1234"));
        assertEquals(1, user.getAuthVersion());
        persistence.users.put(userId, user);

        PersistenceRegistry registry = createTestRegistry(persistence);
        AuthenticationFilter filter = new AuthenticationFilter();

        final boolean[] sessionInvalidated = new boolean[]{false};
        HttpSession session = (HttpSession) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpSession.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAttribute" -> {
                        if (SessionService.USER_ID.equals(args[0])) yield userId;
                        if (SessionService.AUTH_VERSION.equals(args[0])) yield 0;
                        yield null;
                    }
                    case "invalidate" -> {
                        sessionInvalidated[0] = true;
                        yield null;
                    }
                    default -> null;
                });

        ServletContext servletContext = (ServletContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ServletContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAttribute" -> {
                        if (PersistenceListener.REGISTRY_ATTRIBUTE.equals(args[0])) yield registry;
                        yield null;
                    }
                    default -> null;
                });

        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getRequestURI" -> "/context/api/tickets/my-purchases";
                    case "getContextPath" -> "/context";
                    case "getMethod" -> "GET";
                    case "getSession" -> Boolean.FALSE.equals(args[0]) ? session : null;
                    case "getServletContext" -> servletContext;
                    default -> null;
                });

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

        filter.doFilter(request, response, (req, res) -> fail("Filter chain must not proceed when authVersion is mismatched"));

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, status[0]);
        assertTrue(sessionInvalidated[0], "Stale session must be invalidated");
        assertTrue(writer.toString().contains("AUTHENTICATION_REQUIRED"));
    }

    private PersistenceRegistry createTestRegistry(InMemoryPersistence persistence) throws Exception {
        PersistenceRegistry registry = new PersistenceRegistry();
        Field field = PersistenceRegistry.class.getDeclaredField("factories");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<DatabasePrincipal, jakarta.persistence.EntityManagerFactory> map =
                (Map<DatabasePrincipal, jakarta.persistence.EntityManagerFactory>) field.get(registry);
        jakarta.persistence.EntityManagerFactory factory = (jakarta.persistence.EntityManagerFactory) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{jakarta.persistence.EntityManagerFactory.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "createEntityManager" -> persistence.mockEntityManager();
                    default -> null;
                });
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

    private static final class InMemoryPersistence {
        final Map<UUID, User> users = new HashMap<>();
        final List<Otp> otps = new ArrayList<>();

        User findUser(String email) {
            return users.values().stream()
                    .filter(u -> u.getNormalizedEmail().equalsIgnoreCase(email))
                    .findFirst()
                    .orElse(null);
        }

        TransactionManager manager() {
            var factory = (jakarta.persistence.EntityManagerFactory) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{jakarta.persistence.EntityManagerFactory.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "createEntityManager" -> mockEntityManager();
                        default -> null;
                    });
            return new TransactionManager(Map.of(DatabasePrincipal.AUTH, factory));
        }

        private jakarta.persistence.EntityManager mockEntityManager() {
            var transaction = (jakarta.persistence.EntityTransaction) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{jakarta.persistence.EntityTransaction.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "isActive" -> false;
                        default -> null;
                    });

            return (jakarta.persistence.EntityManager) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{jakarta.persistence.EntityManager.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getTransaction" -> transaction;
                        case "flush", "close" -> null;
                        case "find" -> {
                            if (args[0] == User.class) {
                                yield users.get(args[1]);
                            }
                            yield null;
                        }
                        case "persist" -> {
                            if (args[0] instanceof Otp o) {
                                otps.add(o);
                            } else if (args[0] instanceof User u) {
                                users.put(u.getId(), u);
                            }
                            yield null;
                        }
                        case "createQuery" -> createQueryMock((String) args[0]);
                        default -> null;
                    });
        }

        @SuppressWarnings("unchecked")
        private Object createQueryMock(String ql) {
            final Map<String, Object> params = new HashMap<>();
            return Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{jakarta.persistence.TypedQuery.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "setParameter" -> {
                            params.put((String) args[0], args[1]);
                            yield proxy;
                        }
                        case "setLockMode", "setMaxResults" -> proxy;
                        case "executeUpdate" -> {
                            if (ql.startsWith("update Otp")) {
                                otps.forEach(otp -> {
                                    if (otp.getConsumedAt() == null && otp.getInvalidatedAt() == null) {
                                        otp.invalidate(Instant.now());
                                    }
                                });
                            }
                            yield 1;
                        }
                        case "getResultStream" -> {
                            if (ql.contains("User")) {
                                String email = (String) params.get("email");
                                yield users.values().stream()
                                        .filter(u -> email == null || u.getNormalizedEmail().equalsIgnoreCase(email));
                            }
                            if (ql.contains("Otp")) {
                                String email = (String) params.get("email");
                                OtpPurpose purpose = (OtpPurpose) params.get("purpose");
                                yield otps.stream()
                                        .filter(o -> email == null || o.getEmailNormalized().equalsIgnoreCase(email))
                                        .filter(o -> purpose == null || o.getPurpose() == purpose)
                                        .filter(o -> !ql.contains("consumedAt is null") || o.getConsumedAt() == null)
                                        .filter(o -> !ql.contains("invalidatedAt is null") || o.getInvalidatedAt() == null)
                                        .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
                            }
                            yield java.util.stream.Stream.empty();
                        }
                        default -> null;
                    });
        }
    }
}
