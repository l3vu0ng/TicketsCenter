package vn.ticketscenter.identity;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.integration.mail.ConfiguredMailGateway;
import vn.ticketscenter.model.identity.Otp;
import vn.ticketscenter.model.identity.OtpPurpose;
import vn.ticketscenter.service.identity.OtpService;
import vn.ticketscenter.transaction.DatabasePrincipal;
import vn.ticketscenter.transaction.TransactionManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

        clock.advance(Duration.ofSeconds(59));
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.sendOtp(UUID.randomUUID(), "user@example.com", OtpPurpose.VERIFY_EMAIL));
        assertTrue(exception.getMessage().contains("cooldown"));

        clock.advance(Duration.ofSeconds(1));
        assertDoesNotThrow(() -> service.sendOtp(UUID.randomUUID(), "user@example.com", OtpPurpose.VERIFY_EMAIL));
        assertEquals(2, mail.getDispatchedMessages().size());
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
    void rejectsExpiredOtpAfterFiveMinutes() {
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T10:00:00Z"));
        InMemoryTransactionManager transactions = new InMemoryTransactionManager();

        OtpService service = new OtpService(transactions.manager(), mail, clock, TEST_SECRET, () -> "999888");
        service.sendOtp(UUID.randomUUID(), "user@example.com", OtpPurpose.VERIFY_EMAIL);

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        var result = service.verifyOtp("user@example.com", "999888", OtpPurpose.VERIFY_EMAIL);
        assertFalse(result.successful());
        assertEquals("OTP_EXPIRED", result.error());
    }

    @Test
    void successfullyVerifiesOtpAndMarksConsumed() {
        ConfiguredMailGateway mail = new ConfiguredMailGateway();
        MutableClock clock = new MutableClock(Instant.parse("2026-09-27T10:00:00Z"));
        InMemoryTransactionManager transactions = new InMemoryTransactionManager();
        UUID userId = UUID.randomUUID();

        OtpService service = new OtpService(transactions.manager(), mail, clock, TEST_SECRET, () -> "112233");
        service.sendOtp(userId, "valid@example.com", OtpPurpose.VERIFY_EMAIL);

        var result = service.verifyOtp("valid@example.com", "112233", OtpPurpose.VERIFY_EMAIL);
        assertTrue(result.successful());
        assertEquals(userId, result.userId());

        Otp otp = transactions.savedOtps.getFirst();
        assertNotNull(otp.getConsumedAt());

        var secondAttempt = service.verifyOtp("valid@example.com", "112233", OtpPurpose.VERIFY_EMAIL);
        assertFalse(secondAttempt.successful());
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
            EntityManagerFactory factory = (EntityManagerFactory) java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{EntityManagerFactory.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "createEntityManager" -> mockEntityManager();
                        default -> null;
                    });
            return new TransactionManager(Map.of(DatabasePrincipal.AUTH, factory));
        }

        private jakarta.persistence.EntityManager mockEntityManager() {
            var transaction = (jakarta.persistence.EntityTransaction) java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{jakarta.persistence.EntityTransaction.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "isActive" -> false;
                        default -> null;
                    });

            return (jakarta.persistence.EntityManager) java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{jakarta.persistence.EntityManager.class},
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
            return java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{jakarta.persistence.TypedQuery.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "setParameter" -> proxy;
                        case "setLockMode" -> proxy;
                        case "setMaxResults" -> proxy;
                        case "executeUpdate" -> {
                            if (ql.startsWith("update Otp")) {
                                savedOtps.forEach(otp -> {
                                    if (otp.getConsumedAt() == null && otp.getInvalidatedAt() == null) {
                                        otp.invalidate(Instant.now());
                                    }
                                });
                            }
                            yield 1;
                        }
                        case "getResultStream" -> {
                            if (ql.contains("tc_otps") || ql.contains("Otp")) {
                                if (ql.contains("consumedAt is null") && ql.contains("invalidatedAt is null")) {
                                    yield savedOtps.stream()
                                            .filter(o -> o.getConsumedAt() == null && o.getInvalidatedAt() == null)
                                            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
                                }
                                yield savedOtps.stream().sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
                            }
                            yield java.util.stream.Stream.empty();
                        }
                        default -> null;
                    });
        }
    }
}
