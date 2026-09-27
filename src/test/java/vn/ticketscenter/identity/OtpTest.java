package vn.ticketscenter.identity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.integration.mail.ConfiguredMailGateway;
import vn.ticketscenter.identity.model.Otp;
import vn.ticketscenter.identity.model.OtpPurpose;
import vn.ticketscenter.identity.service.OtpService;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OtpTest {

    private static final byte[] TEST_SECRET = "test-secret-key-1234567890123456".getBytes(StandardCharsets.UTF_8);

    @Test
    void preservesSixDigitsWithLeadingZeroes() {
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T10:00:00Z"));
        AtomicInteger counter = new AtomicInteger(1);
        InMemoryTransactionManager transactions = new InMemoryTransactionManager();

        OtpService service = new OtpService(transactions.manager(), mail, clock, TEST_SECRET,
                () -> String.format("%06d", counter.getAndIncrement()));

        service.sendOtp(UUID.randomUUID(), "buyer@example.com", OtpPurpose.VERIFY_EMAIL);

        assertEquals(1, mail.getDispatchedMessages().size());
        String code = mail.getDispatchedMessages().getFirst().otpCode();
        assertEquals("000001", code);
        assertEquals(6, code.length());

        Otp savedOtp = transactions.savedOtps.getFirst();
        assertNull(savedOtp.getConsumedAt());
        assertNull(savedOtp.getInvalidatedAt());
        assertEquals(0, savedOtp.getFailedAttempts());
        assertNotNull(savedOtp.getSecretHash());
        assertNotEquals("000001", new String(savedOtp.getSecretHash(), StandardCharsets.UTF_8));
    }

    @Test
    void blocksResendAtFiftyNineSecondsAndAllowsAtSixtySeconds() {
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T10:00:00Z"));
        InMemoryTransactionManager transactions = new InMemoryTransactionManager();

        OtpService service = new OtpService(transactions.manager(), mail, clock, TEST_SECRET, () -> "123456");

        service.sendOtp(UUID.randomUUID(), "user@example.com", OtpPurpose.VERIFY_EMAIL);

        clock.advance(Duration.ofMillis(59_900));
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.sendOtp(UUID.randomUUID(), "user@example.com", OtpPurpose.VERIFY_EMAIL));
        assertTrue(exception.getMessage().contains("cooldown"));

        clock.advance(Duration.ofMillis(100));
        assertDoesNotThrow(() -> service.sendOtp(UUID.randomUUID(), "user@example.com", OtpPurpose.VERIFY_EMAIL));
        assertEquals(2, mail.getDispatchedMessages().size());
    }

    @Test
    void rateLimitsAccountAfterFiveSendsInWindow() {
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T10:00:00Z"));
        InMemoryTransactionManager transactions = new InMemoryTransactionManager();

        OtpService service = new OtpService(transactions.manager(), mail, clock, TEST_SECRET, () -> "111222");

        for (int i = 0; i < 5; i++) {
            service.sendOtp(UUID.randomUUID(), "limit@example.com", OtpPurpose.VERIFY_EMAIL);
            clock.advance(Duration.ofSeconds(60));
        }

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.sendOtp(UUID.randomUUID(), "limit@example.com", OtpPurpose.VERIFY_EMAIL));
        assertTrue(ex.getMessage().contains("Account rate limit"));
    }

    @Test
    void rateLimitsIpAfterTenSendsInWindow() {
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T10:00:00Z"));
        InMemoryTransactionManager transactions = new InMemoryTransactionManager();

        OtpService service = new OtpService(transactions.manager(), mail, clock, TEST_SECRET, () -> "333444");

        for (int i = 1; i <= 10; i++) {
            service.sendOtp(UUID.randomUUID(), "user" + i + "@example.com", OtpPurpose.VERIFY_EMAIL, "203.0.113.195");
            clock.advance(Duration.ofSeconds(2));
        }

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.sendOtp(UUID.randomUUID(), "user11@example.com", OtpPurpose.VERIFY_EMAIL, "203.0.113.195"));
        assertTrue(ex.getMessage().contains("IP rate limit"));
    }

    @Test
    void differentPurposesDoNotInvalidateEachOther() {
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T10:00:00Z"));
        InMemoryTransactionManager transactions = new InMemoryTransactionManager();

        OtpService service = new OtpService(transactions.manager(), mail, clock, TEST_SECRET, () -> "555777");
        UUID userId = UUID.randomUUID();

        service.sendOtp(userId, "dual@example.com", OtpPurpose.VERIFY_EMAIL);
        Otp verifyOtp = transactions.savedOtps.getFirst();

        clock.advance(Duration.ofSeconds(5));
        service.sendOtp(userId, "dual@example.com", OtpPurpose.RESET_PASSWORD);
        Otp resetOtp = transactions.savedOtps.get(1);

        assertNull(verifyOtp.getInvalidatedAt());
        assertNull(resetOtp.getInvalidatedAt());
    }

    @Test
    void invalidatesPreviousActiveCodeWhenResent() {
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T10:00:00Z"));
        InMemoryTransactionManager transactions = new InMemoryTransactionManager();

        AtomicInteger codes = new AtomicInteger(100000);
        OtpService service = new OtpService(transactions.manager(), mail, clock, TEST_SECRET, () -> String.valueOf(codes.getAndIncrement()));

        service.sendOtp(UUID.randomUUID(), "user@example.com", OtpPurpose.VERIFY_EMAIL);
        Otp firstOtp = transactions.savedOtps.getFirst();

        clock.advance(Duration.ofSeconds(60));
        service.sendOtp(UUID.randomUUID(), "user@example.com", OtpPurpose.VERIFY_EMAIL);

        assertNotNull(firstOtp.getInvalidatedAt());
        Otp secondOtp = transactions.savedOtps.get(1);
        assertNull(secondOtp.getInvalidatedAt());
    }

    @Test
    void rejectsVerificationAfterFiveFailedAttempts() {
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T10:00:00Z"));
        InMemoryTransactionManager transactions = new InMemoryTransactionManager();

        OtpService service = new OtpService(transactions.manager(), mail, clock, TEST_SECRET, () -> "654321");
        service.sendOtp(UUID.randomUUID(), "verify@example.com", OtpPurpose.VERIFY_EMAIL);

        for (int i = 0; i < 4; i++) {
            var result = service.verifyOtp("verify@example.com", "000000", OtpPurpose.VERIFY_EMAIL);
            assertFalse(result.successful());
            assertEquals("OTP_INVALID", result.error());
        }

        Otp otp = transactions.savedOtps.getFirst();
        assertEquals(4, otp.getFailedAttempts());
        assertNull(otp.getInvalidatedAt());

        var fifthFailure = service.verifyOtp("verify@example.com", "000000", OtpPurpose.VERIFY_EMAIL);
        assertFalse(fifthFailure.successful());
        assertEquals(5, otp.getFailedAttempts());
        assertNotNull(otp.getInvalidatedAt());

        var subsequent = service.verifyOtp("verify@example.com", "654321", OtpPurpose.VERIFY_EMAIL);
        assertFalse(subsequent.successful());
        assertEquals("OTP_NOT_FOUND", subsequent.error());
    }

    @Test
    void boundaryTtlValidationAroundFiveMinutes() {
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T10:00:00Z"));
        InMemoryTransactionManager transactions = new InMemoryTransactionManager();

        OtpService service = new OtpService(transactions.manager(), mail, clock, TEST_SECRET, () -> "999888");
        service.sendOtp(UUID.randomUUID(), "user@example.com", OtpPurpose.VERIFY_EMAIL);

        clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        var validAtBoundary = service.verifyOtp("user@example.com", "999888", OtpPurpose.VERIFY_EMAIL);
        assertTrue(validAtBoundary.successful());

        clock.advance(Duration.ofSeconds(60));
        service.sendOtp(UUID.randomUUID(), "user@example.com", OtpPurpose.VERIFY_EMAIL);
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        var expiredAfterFiveMin = service.verifyOtp("user@example.com", "999888", OtpPurpose.VERIFY_EMAIL);
        assertFalse(expiredAfterFiveMin.successful());
        assertEquals("OTP_EXPIRED", expiredAfterFiveMin.error());
    }

    @Test
    void rejectsBlankOrEmptyCode() {
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T10:00:00Z"));
        InMemoryTransactionManager transactions = new InMemoryTransactionManager();

        OtpService service = new OtpService(transactions.manager(), mail, clock, TEST_SECRET, () -> "123456");
        service.sendOtp(UUID.randomUUID(), "test@example.com", OtpPurpose.VERIFY_EMAIL);

        var nullCode = service.verifyOtp("test@example.com", null, OtpPurpose.VERIFY_EMAIL);
        assertFalse(nullCode.successful());
        assertEquals("OTP_REQUIRED", nullCode.error());

        var blankCode = service.verifyOtp("test@example.com", "   ", OtpPurpose.VERIFY_EMAIL);
        assertFalse(blankCode.successful());
        assertEquals("OTP_REQUIRED", blankCode.error());
    }

    @Test
    void emailNormalizationHandlesCaseAndWhitespace() {
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T10:00:00Z"));
        InMemoryTransactionManager transactions = new InMemoryTransactionManager();

        OtpService service = new OtpService(transactions.manager(), mail, clock, TEST_SECRET, () -> "334455");
        service.sendOtp(UUID.randomUUID(), "CaseUser@Example.Com", OtpPurpose.VERIFY_EMAIL);

        var result = service.verifyOtp("  caseuser@example.com  ", "334455", OtpPurpose.VERIFY_EMAIL);
        assertTrue(result.successful());
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

    private static final class InMemoryTransactionManager {
        final List<Otp> savedOtps = new ArrayList<>();

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
                        case "persist" -> {
                            if (args[0] instanceof Otp otp) {
                                savedOtps.add(otp);
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
                                String email = (String) params.get("email");
                                OtpPurpose purpose = (OtpPurpose) params.get("purpose");
                                savedOtps.forEach(otp -> {
                                    if ((email == null || otp.getEmailNormalized().equalsIgnoreCase(email))
                                            && (purpose == null || otp.getPurpose() == purpose)
                                            && otp.getConsumedAt() == null && otp.getInvalidatedAt() == null) {
                                        otp.invalidate(Instant.now());
                                    }
                                });
                            }
                            yield 1;
                        }
                        case "getResultStream" -> {
                            if (ql.contains("tc_otps") || ql.contains("Otp")) {
                                String email = (String) params.get("email");
                                OtpPurpose purpose = (OtpPurpose) params.get("purpose");
                                var stream = savedOtps.stream();
                                if (email != null) {
                                    stream = stream.filter(o -> o.getEmailNormalized().equalsIgnoreCase(email));
                                }
                                if (purpose != null) {
                                    stream = stream.filter(o -> o.getPurpose() == purpose);
                                }
                                if (ql.contains("consumedAt is null")) {
                                    stream = stream.filter(o -> o.getConsumedAt() == null);
                                }
                                if (ql.contains("invalidatedAt is null")) {
                                    stream = stream.filter(o -> o.getInvalidatedAt() == null);
                                }
                                yield stream.sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
                            }
                            yield java.util.stream.Stream.empty();
                        }
                        default -> null;
                    });
        }
    }
}
