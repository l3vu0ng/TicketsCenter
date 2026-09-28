package vn.ticketscenter.identity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.integration.mail.ConfiguredMailGateway;
import vn.ticketscenter.identity.model.Otp;
import vn.ticketscenter.identity.model.IdentityEnums;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.service.AccountService;
import vn.ticketscenter.identity.service.OtpService;
import vn.ticketscenter.identity.service.PasswordHasher;
import vn.ticketscenter.identity.service.PasswordResetService;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;

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

        otpService.sendOtp(charlieId, "charlie@example.com", IdentityEnums.OtpPurpose.VERIFY_EMAIL);

        var verifyResult = resetService.verifyResetOtp("charlie@example.com", "555666");
        assertFalse(verifyResult.successful());
        assertEquals("OTP_NOT_FOUND", verifyResult.error());
    }

    @Test
    void tokenTamperingThrowsSecurityExceptions() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T12:00:00Z"));
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        PasswordHasher hasher = new PasswordHasher();
        TestPersistence persistence = new TestPersistence();

        UUID targetUserId = UUID.randomUUID();
        User targetUser = new User(targetUserId, "victim@example.com", "victim@example.com", hasher.hash("OldPassword1234"), "Victim", clock.instant());
        persistence.users.put(targetUserId, targetUser);

        OtpService otpService = new OtpService(persistence.manager(), mail, clock, TEST_SECRET, () -> "111222");
        PasswordResetService resetService = new PasswordResetService(persistence.manager(), otpService, hasher, clock, TEST_SECRET);

        resetService.requestReset("victim@example.com");
        var verifyResult = resetService.verifyResetOtp("victim@example.com", "111222");
        assertTrue(verifyResult.successful());
        String validToken = verifyResult.token();

        String[] parts = validToken.split("\\.");
        assertEquals(3, parts.length);
        String userIdStr = parts[0];
        String expiryStr = parts[1];
        String signatureB64 = parts[2];

        String tamperedSigToken = userIdStr + "." + expiryStr + "." + signatureB64.substring(0, signatureB64.length() - 2) + "==";
        IllegalArgumentException sigEx = assertThrows(IllegalArgumentException.class,
                () -> resetService.resetPassword(tamperedSigToken, "NewSecurePassword999"));
        assertTrue(sigEx.getMessage().contains("Invalid") || sigEx.getMessage().contains("consumed"));

        long extendedExpiry = Long.parseLong(expiryStr) + 3600000;
        String tamperedExpiryToken = userIdStr + "." + extendedExpiry + "." + signatureB64;
        assertThrows(IllegalArgumentException.class,
                () -> resetService.resetPassword(tamperedExpiryToken, "NewSecurePassword999"));

        UUID attackerId = UUID.randomUUID();
        User attacker = new User(attackerId, "attacker@example.com", "attacker@example.com", hasher.hash("AttackerPass123"), "Attacker", clock.instant());
        persistence.users.put(attackerId, attacker);
        String tamperedUserToken = attackerId + "." + expiryStr + "." + signatureB64;
        assertThrows(IllegalArgumentException.class,
                () -> resetService.resetPassword(tamperedUserToken, "NewSecurePassword999"));

        assertThrows(IllegalArgumentException.class, () -> resetService.resetPassword(null, "NewSecurePassword999"));
        assertThrows(IllegalArgumentException.class, () -> resetService.resetPassword("   ", "NewSecurePassword999"));
        assertThrows(IllegalArgumentException.class, () -> resetService.resetPassword("singleparttoken", "NewSecurePassword999"));
        assertThrows(IllegalArgumentException.class, () -> resetService.resetPassword(userIdStr + "." + expiryStr, "NewSecurePassword999"));
        assertThrows(IllegalArgumentException.class, () -> resetService.resetPassword("not-a-uuid." + expiryStr + "." + signatureB64, "NewSecurePassword999"));
    }

    @Test
    void passwordValidationEnforcesBoundariesOnReset() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T12:00:00Z"));
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        PasswordHasher hasher = new PasswordHasher();
        TestPersistence persistence = new TestPersistence();

        UUID userId = UUID.randomUUID();
        User user = new User(userId, "boundary@example.com", "boundary@example.com", hasher.hash("OldPassword1234"), "Boundary", clock.instant());
        persistence.users.put(userId, user);

        OtpService otpService = new OtpService(persistence.manager(), mail, clock, TEST_SECRET, () -> "999888");
        PasswordResetService resetService = new PasswordResetService(persistence.manager(), otpService, hasher, clock, TEST_SECRET);

        resetService.requestReset("boundary@example.com");
        var verifyResult = resetService.verifyResetOtp("boundary@example.com", "999888");
        assertTrue(verifyResult.successful());
        String token = verifyResult.token();

        String pass11 = "1234567890a";
        assertEquals(11, pass11.length());
        assertThrows(IllegalArgumentException.class, () -> resetService.resetPassword(token, pass11));

        String pass257 = "a".repeat(257);
        assertEquals(257, pass257.length());
        assertThrows(IllegalArgumentException.class, () -> resetService.resetPassword(token, pass257));

        assertThrows(IllegalArgumentException.class, () -> resetService.resetPassword(token, null));
        assertThrows(IllegalArgumentException.class, () -> resetService.resetPassword(token, ""));

        String pass12 = "1234567890ab";
        assertEquals(12, pass12.length());
        assertDoesNotThrow(() -> resetService.resetPassword(token, pass12));
        assertTrue(hasher.matches(pass12, user.getPasswordHash()));

        clock.advance(Duration.ofSeconds(60));
        resetService.requestReset("boundary@example.com");
        var secondVerify = resetService.verifyResetOtp("boundary@example.com", "999888");
        assertTrue(secondVerify.successful());
        String pass256 = "b".repeat(256);
        assertEquals(256, pass256.length());
        assertDoesNotThrow(() -> resetService.resetPassword(secondVerify.token(), pass256));
        assertTrue(hasher.matches(pass256, user.getPasswordHash()));
    }

    @Test
    void inactiveUserCannotInitiateOrCompletePasswordReset() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T12:00:00Z"));
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        PasswordHasher hasher = new PasswordHasher();
        TestPersistence persistence = new TestPersistence();

        UUID suspendedId = UUID.randomUUID();
        User suspendedUser = new User(suspendedId, "suspended@example.com", "suspended@example.com", hasher.hash("OldPassword1234"), "Suspended", clock.instant());
        suspendedUser.setStatus(vn.ticketscenter.identity.model.IdentityEnums.UserStatus.DISABLED);
        persistence.users.put(suspendedId, suspendedUser);

        OtpService otpService = new OtpService(persistence.manager(), mail, clock, TEST_SECRET, () -> "444555");
        PasswordResetService resetService = new PasswordResetService(persistence.manager(), otpService, hasher, clock, TEST_SECRET);

        resetService.requestReset("suspended@example.com");
        assertEquals(0, mail.getDispatchedMessages().size());

        UUID victimId = UUID.randomUUID();
        User victimUser = new User(victimId, "victim_active@example.com", "victim_active@example.com", hasher.hash("OldPassword1234"), "Victim", clock.instant());
        persistence.users.put(victimId, victimUser);

        resetService.requestReset("victim_active@example.com");
        var verifyResult = resetService.verifyResetOtp("victim_active@example.com", "444555");
        assertTrue(verifyResult.successful());

        victimUser.setStatus(vn.ticketscenter.identity.model.IdentityEnums.UserStatus.DISABLED);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> resetService.resetPassword(verifyResult.token(), "NewPassword1234"));
        assertTrue(ex.getMessage().contains("inactive"));
    }

    @Test
    void passwordResetRevokesConcurrentSessionsAcrossAllDevices() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T12:00:00Z"));
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        PasswordHasher hasher = new PasswordHasher();
        TestPersistence persistence = new TestPersistence();

        UUID userId = UUID.randomUUID();
        User user = new User(userId, "multi_device@example.com", "multi_device@example.com", hasher.hash("InitialPassword123"), "MultiDevice", clock.instant());
        persistence.users.put(userId, user);

        AccountService accountService = new AccountService(persistence.manager(), hasher, clock);
        OtpService otpService = new OtpService(persistence.manager(), mail, clock, TEST_SECRET, () -> "777111");
        PasswordResetService resetService = new PasswordResetService(persistence.manager(), otpService, hasher, clock, TEST_SECRET);

        var deviceA = accountService.authenticate("multi_device@example.com", "InitialPassword123").orElseThrow();
        var deviceB = accountService.authenticate("multi_device@example.com", "InitialPassword123").orElseThrow();
        assertEquals(0, deviceA.authVersion());
        assertEquals(0, deviceB.authVersion());

        resetService.requestReset("multi_device@example.com");
        var verifyResult = resetService.verifyResetOtp("multi_device@example.com", "777111");
        assertTrue(verifyResult.successful());
        resetService.resetPassword(verifyResult.token(), "NewChangedPassword123");

        var currentAccount = accountService.current(userId).orElseThrow();
        assertEquals(1, currentAccount.authVersion());

        assertNotEquals(currentAccount.authVersion(), deviceA.authVersion());
        assertNotEquals(currentAccount.authVersion(), deviceB.authVersion());

        assertFalse(accountService.authenticate("multi_device@example.com", "InitialPassword123").isPresent());

        var newLogin = accountService.authenticate("multi_device@example.com", "NewChangedPassword123").orElseThrow();
        assertEquals(1, newLogin.authVersion());
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
                                IdentityEnums.OtpPurpose purpose = (IdentityEnums.OtpPurpose) params.get("purpose");
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
