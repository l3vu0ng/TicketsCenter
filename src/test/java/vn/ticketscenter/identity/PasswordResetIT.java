package vn.ticketscenter.identity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.integration.mail.ConfiguredMailGateway;
import vn.ticketscenter.model.identity.Otp;
import vn.ticketscenter.model.identity.OtpPurpose;
import vn.ticketscenter.model.identity.User;
import vn.ticketscenter.service.identity.AccountService;
import vn.ticketscenter.service.identity.OtpService;
import vn.ticketscenter.service.identity.PasswordHasher;
import vn.ticketscenter.service.identity.PasswordResetService;
import vn.ticketscenter.transaction.DatabasePrincipal;
import vn.ticketscenter.transaction.TransactionManager;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class PasswordResetIT {

    private static final byte[] TEST_SECRET = "password-reset-test-secret-12345".getBytes(StandardCharsets.UTF_8);

    @Test
    void successfullyResetsPasswordIncrementsAuthVersionAndInvalidatesReuse() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T12:00:00Z"));
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        PasswordHasher hasher = new PasswordHasher();
        TestPersistence persistence = new TestPersistence();

        UUID aliceId = UUID.randomUUID();
        User user = new User(aliceId, "alice@example.com", "alice@example.com", hasher.hash("OldPassword1234"), "Alice", clock.instant());
        persistence.users.put(aliceId, user);

        OtpService otpService = new OtpService(persistence.manager(), mail, clock, TEST_SECRET, () -> "888999");
        PasswordResetService resetService = new PasswordResetService(persistence.manager(), otpService, hasher, clock, TEST_SECRET);
        AccountService accountService = new AccountService(persistence.manager(), hasher, clock);

        resetService.requestReset("alice@example.com");
        assertEquals(1, mail.getDispatchedMessages().size());
        assertEquals("888999", mail.getDispatchedMessages().getFirst().otpCode());

        var verifyResult = resetService.verifyResetOtp("alice@example.com", "888999");
        assertTrue(verifyResult.successful());
        assertNotNull(verifyResult.token());

        int initialAuthVersion = user.getAuthVersion();

        resetService.resetPassword(verifyResult.token(), "NewSecurePassword999");

        assertEquals(initialAuthVersion + 1, user.getAuthVersion());

        assertFalse(accountService.authenticate("alice@example.com", "OldPassword1234").isPresent());
        assertTrue(accountService.authenticate("alice@example.com", "NewSecurePassword999").isPresent());

        IllegalArgumentException reuseEx = assertThrows(IllegalArgumentException.class,
                () -> resetService.resetPassword(verifyResult.token(), "AnotherPassword888"));
        assertTrue(reuseEx.getMessage().contains("consumed") || reuseEx.getMessage().contains("Invalid"));
    }

    @Test
    void rejectsExpiredResetToken() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T12:00:00Z"));
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        PasswordHasher hasher = new PasswordHasher();
        TestPersistence persistence = new TestPersistence();

        UUID bobId = UUID.randomUUID();
        User user = new User(bobId, "bob@example.com", "bob@example.com", hasher.hash("OldPassword1234"), "Bob", clock.instant());
        persistence.users.put(bobId, user);

        OtpService otpService = new OtpService(persistence.manager(), mail, clock, TEST_SECRET, () -> "123123");
        PasswordResetService resetService = new PasswordResetService(persistence.manager(), otpService, hasher, clock, TEST_SECRET);

        resetService.requestReset("bob@example.com");
        var verifyResult = resetService.verifyResetOtp("bob@example.com", "123123");
        assertTrue(verifyResult.successful());

        clock.advance(Duration.ofMinutes(10).plusSeconds(1));

        assertThrows(IllegalArgumentException.class,
                () -> resetService.resetPassword(verifyResult.token(), "BrandNewPassword123"));
    }

    @Test
    void rejectsVerifyEmailOtpForPasswordReset() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T12:00:00Z"));
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        PasswordHasher hasher = new PasswordHasher();
        TestPersistence persistence = new TestPersistence();

        UUID charlieId = UUID.randomUUID();
        User user = new User(charlieId, "charlie@example.com", "charlie@example.com", hasher.hash("OldPassword1234"), "Charlie", clock.instant());
        persistence.users.put(charlieId, user);

        OtpService otpService = new OtpService(persistence.manager(), mail, clock, TEST_SECRET, () -> "555666");
        PasswordResetService resetService = new PasswordResetService(persistence.manager(), otpService, hasher, clock, TEST_SECRET);

        otpService.sendOtp(charlieId, "charlie@example.com", OtpPurpose.VERIFY_EMAIL);

        var verifyResult = resetService.verifyResetOtp("charlie@example.com", "555666");
        assertFalse(verifyResult.successful());
        assertEquals("OTP_NOT_FOUND", verifyResult.error());
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

    private static final class TestPersistence {
        final Map<UUID, User> users = new HashMap<>();
        final List<Otp> otps = new ArrayList<>();

        TransactionManager manager() {
            EntityManagerFactory factory = (EntityManagerFactory) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{EntityManagerFactory.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "createEntityManager" -> mockEntityManager();
                        default -> null;
                    });
            return new TransactionManager(Map.of(DatabasePrincipal.AUTH, factory));
        }

        private EntityManager mockEntityManager() {
            EntityTransaction transaction = (EntityTransaction) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{EntityTransaction.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "isActive" -> false;
                        default -> null;
                    });

            return (EntityManager) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{EntityManager.class},
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
                            if (args[0] instanceof Otp otp) {
                                otps.add(otp);
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
                    getClass().getClassLoader(), new Class<?>[]{TypedQuery.class},
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
