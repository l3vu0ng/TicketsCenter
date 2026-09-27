package vn.ticketscenter.acceptance;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.PersistenceListener;
import vn.ticketscenter.config.PersistenceRegistry;
import vn.ticketscenter.controller.identity.AuthServlet;
import vn.ticketscenter.controller.identity.OtpServlet;
import vn.ticketscenter.filter.AuthenticationFilter;
import vn.ticketscenter.filter.CsrfFilter;
import vn.ticketscenter.filter.RequestValidationFilter;
import vn.ticketscenter.integration.mail.ConfiguredMailGateway;
import vn.ticketscenter.model.ModelEnums;
import vn.ticketscenter.model.identity.Otp;
import vn.ticketscenter.model.identity.OtpPurpose;
import vn.ticketscenter.model.identity.User;
import vn.ticketscenter.service.identity.AccountService;
import vn.ticketscenter.service.identity.OtpService;
import vn.ticketscenter.service.identity.PasswordHasher;
import vn.ticketscenter.service.identity.PasswordResetService;
import vn.ticketscenter.transaction.DatabasePrincipal;
import vn.ticketscenter.transaction.TransactionManager;

import java.io.*;
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
