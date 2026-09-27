package vn.ticketscenter.identity.service;

import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.identity.integration.mail.MailGateway;
import vn.ticketscenter.identity.model.Otp;
import vn.ticketscenter.identity.model.OtpPurpose;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.repository.OtpRepository;
import vn.ticketscenter.identity.repository.UserRepository;
import vn.ticketscenter.identity.service.AccountService;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class OtpService {

    public static final Duration COOLDOWN = Duration.ofSeconds(60);
    public static final Duration TTL = Duration.ofMinutes(5);
    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final int MAX_SENDS_PER_ACCOUNT_WINDOW = 5;
    public static final int MAX_SENDS_PER_IP_WINDOW = 10;
    public static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(15);

    private final TransactionManager transactions;
    private final MailGateway mailGateway;
    private final Clock clock;
    private final byte[] secretKey;
    private final Supplier<String> codeGenerator;
    private final Map<String, List<Instant>> sendHistory = new ConcurrentHashMap<>();

    public record VerifyResult(boolean successful, UUID userId, String error) {
        public static VerifyResult success(UUID userId) {
            return new VerifyResult(true, userId, null);
        }

        public static VerifyResult failure(String error) {
            return new VerifyResult(false, null, error);
        }
    }

    public OtpService(TransactionManager transactions, MailGateway mailGateway, Clock clock) {
        this(transactions, mailGateway, clock, resolveSecretKey(), defaultGenerator());
    }

    public OtpService(TransactionManager transactions, MailGateway mailGateway, Clock clock,
                      byte[] secretKey, Supplier<String> codeGenerator) {
        this.transactions = Objects.requireNonNull(transactions, "transactions is required");
        this.mailGateway = Objects.requireNonNull(mailGateway, "mailGateway is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.secretKey = Objects.requireNonNull(secretKey, "secretKey is required");
        this.codeGenerator = Objects.requireNonNull(codeGenerator, "codeGenerator is required");
    }

    public void sendOtp(UUID userId, String rawEmail, OtpPurpose purpose) {
        sendOtp(userId, rawEmail, purpose, null);
    }

    public void sendOtp(UUID userId, String rawEmail, OtpPurpose purpose, String clientIp) {
        Objects.requireNonNull(purpose, "purpose is required");
        String normalizedEmail = AccountService.normalizeEmail(rawEmail);
        Instant now = clock.instant();

        checkRateLimits(normalizedEmail, clientIp, now);

        String code = transactions.execute(DatabasePrincipal.AUTH, entityManager -> {
            OtpRepository repository = new OtpRepository(entityManager);
            Optional<Otp> latest = repository.findLatest(normalizedEmail, purpose);
            if (latest.isPresent()) {
                Instant latestCreated = latest.get().getCreatedAt();
                if (now.isBefore(latestCreated.plus(COOLDOWN))) {
                    throw new IllegalStateException("OTP cooldown active. Please wait before requesting another code.");
                }
            }

            repository.invalidateAllActive(normalizedEmail, purpose, now);

            String generatedCode = codeGenerator.get();
            byte[] hash = computeHash(normalizedEmail, purpose, generatedCode);
            Instant expiresAt = now.plus(TTL);

            Otp otp = new Otp(userId, normalizedEmail, purpose, hash, expiresAt, now);
            repository.save(otp);
            return generatedCode;
        });

        recordSendHistory(normalizedEmail, clientIp, now);
        mailGateway.sendOtp(rawEmail.trim(), code, purpose);
    }

    private void checkRateLimits(String normalizedEmail, String clientIp, Instant now) {
        Instant windowStart = now.minus(RATE_LIMIT_WINDOW);
        if (clientIp != null && !clientIp.isBlank()) {
            List<Instant> ipHistory = sendHistory.getOrDefault("ip:" + clientIp, List.of());
            long ipCount = ipHistory.stream().filter(t -> t.isAfter(windowStart)).count();
            if (ipCount >= MAX_SENDS_PER_IP_WINDOW) {
                throw new IllegalStateException("IP rate limit exceeded. Please wait before requesting another OTP.");
            }
        }

        List<Instant> accountHistory = sendHistory.getOrDefault("email:" + normalizedEmail, List.of());
        long accountCount = accountHistory.stream().filter(t -> t.isAfter(windowStart)).count();
        if (accountCount >= MAX_SENDS_PER_ACCOUNT_WINDOW) {
            throw new IllegalStateException("Account rate limit exceeded. Please wait before requesting another OTP.");
        }
    }

    private void recordSendHistory(String normalizedEmail, String clientIp, Instant now) {
        Instant windowStart = now.minus(RATE_LIMIT_WINDOW);
        sendHistory.compute("email:" + normalizedEmail, (key, list) -> {
            List<Instant> updated = list == null ? new ArrayList<>() : new ArrayList<>(list);
            updated.removeIf(t -> !t.isAfter(windowStart));
            updated.add(now);
            return updated;
        });
        if (clientIp != null && !clientIp.isBlank()) {
            sendHistory.compute("ip:" + clientIp, (key, list) -> {
                List<Instant> updated = list == null ? new ArrayList<>() : new ArrayList<>(list);
                updated.removeIf(t -> !t.isAfter(windowStart));
                updated.add(now);
                return updated;
            });
        }
    }

    public VerifyResult verifyOtp(String rawEmail, String code, OtpPurpose purpose) {
        Objects.requireNonNull(purpose, "purpose is required");
        if (code == null || code.isBlank()) {
            return VerifyResult.failure("OTP_REQUIRED");
        }
        String normalizedEmail = AccountService.normalizeEmail(rawEmail);
        Instant now = clock.instant();

        return transactions.execute(DatabasePrincipal.AUTH, entityManager -> {
            OtpRepository repository = new OtpRepository(entityManager);
            Optional<Otp> found = repository.findActiveWithLock(normalizedEmail, purpose);
            if (found.isEmpty()) {
                return VerifyResult.failure("OTP_NOT_FOUND");
            }

            Otp otp = found.get();
            if (now.isAfter(otp.getExpiresAt())) {
                otp.invalidate(now);
                return VerifyResult.failure("OTP_EXPIRED");
            }

            if (otp.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
                return VerifyResult.failure("OTP_EXHAUSTED");
            }

            byte[] expectedHash = computeHash(normalizedEmail, purpose, code.trim());
            if (!MessageDigest.isEqual(otp.getSecretHash(), expectedHash)) {
                otp.recordFailedAttempt(now);
                return VerifyResult.failure("OTP_INVALID");
            }

            otp.consume(now);
            UUID targetUserId = otp.getUserId();
            UserRepository users = new UserRepository(entityManager);
            if (targetUserId == null) {
                targetUserId = users.findByNormalizedEmail(normalizedEmail).map(User::getId).orElse(null);
            }

            if (purpose == OtpPurpose.VERIFY_EMAIL) {
                if (targetUserId != null) {
                    users.findById(targetUserId).ifPresent(user -> user.verifyEmail(now));
                } else {
                    users.findByNormalizedEmail(normalizedEmail).ifPresent(user -> user.verifyEmail(now));
                }
            }

            return VerifyResult.success(targetUserId);
        });
    }

    public byte[] computeHash(String emailNormalized, OtpPurpose purpose, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            String payload = emailNormalized + ":" + purpose.name() + ":" + code;
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Failed to compute HMAC SHA256", exception);
        }
    }

    private static byte[] resolveSecretKey() {
        String key = AppConfig.get("app.secret.key");
        if (key == null || key.isBlank()) {
            return "default-secret-key-for-ticketscenter-platform".getBytes(StandardCharsets.UTF_8);
        }
        return key.getBytes(StandardCharsets.UTF_8);
    }

    private static Supplier<String> defaultGenerator() {
        SecureRandom random = new SecureRandom();
        return () -> String.format("%06d", random.nextInt(1_000_000));
    }
}
