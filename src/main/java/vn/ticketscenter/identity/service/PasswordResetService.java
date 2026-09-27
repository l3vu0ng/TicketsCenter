package vn.ticketscenter.identity.service;

import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.identity.model.OtpPurpose;
import vn.ticketscenter.identity.model.User;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PasswordResetService {

    public static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(10);

    private final TransactionManager transactions;
    private final OtpService otpService;
    private final PasswordHasher passwordHasher;
    private final Clock clock;
    private final byte[] secretKey;

    public record ResetTokenResult(boolean successful, String token, Instant expiresAt, String error) {
        public static ResetTokenResult success(String token, Instant expiresAt) {
            return new ResetTokenResult(true, token, expiresAt, null);
        }

        public static ResetTokenResult failure(String error) {
            return new ResetTokenResult(false, null, null, error);
        }
    }

    public PasswordResetService(TransactionManager transactions, OtpService otpService,
                                PasswordHasher passwordHasher, Clock clock) {
        this(transactions, otpService, passwordHasher, clock, resolveSecretKey());
    }

    public PasswordResetService(TransactionManager transactions, OtpService otpService,
                                PasswordHasher passwordHasher, Clock clock, byte[] secretKey) {
        this.transactions = Objects.requireNonNull(transactions, "transactions is required");
        this.otpService = Objects.requireNonNull(otpService, "otpService is required");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.secretKey = Objects.requireNonNull(secretKey, "secretKey is required");
    }

    public void requestReset(String rawEmail) {
        requestReset(rawEmail, null);
    }

    public void requestReset(String rawEmail, String clientIp) {
        String normalized = AccountService.normalizeEmail(rawEmail);
        Optional<User> userOpt = transactions.execute(DatabasePrincipal.AUTH, entityManager ->
                new UserRepository(entityManager).findByNormalizedEmail(normalized).filter(User::isActive));

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            otpService.sendOtp(user.getId(), rawEmail, OtpPurpose.RESET_PASSWORD, clientIp);
        }
    }

    public ResetTokenResult verifyResetOtp(String rawEmail, String otpCode) {
        OtpService.VerifyResult result = otpService.verifyOtp(rawEmail, otpCode, OtpPurpose.RESET_PASSWORD);
        if (!result.successful()) {
            return ResetTokenResult.failure(result.error());
        }

        UUID userId = result.userId();
        if (userId == null) {
            return ResetTokenResult.failure("USER_NOT_FOUND");
        }

        Instant expiresAt = clock.instant().plus(RESET_TOKEN_TTL);
        String token = transactions.execute(DatabasePrincipal.AUTH, entityManager -> {
            UserRepository users = new UserRepository(entityManager);
            User user = users.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("User not found for verified OTP"));
            return createToken(user.getId(), expiresAt.toEpochMilli(), user.getPasswordHash());
        });

        return ResetTokenResult.success(token, expiresAt);
    }

    public void resetPassword(String resetToken, String newPassword) {
        AccountService.validatePassword(newPassword);
        ParsedToken parsed = parseToken(resetToken);

        if (clock.instant().toEpochMilli() > parsed.expiresAtEpoch()) {
            throw new IllegalArgumentException("Reset token has expired");
        }

        transactions.execute(DatabasePrincipal.AUTH, entityManager -> {
            UserRepository users = new UserRepository(entityManager);
            User user = users.findById(parsed.userId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid reset token user"));

            if (!user.isActive()) {
                throw new IllegalArgumentException("User account is inactive");
            }

            byte[] expectedSignature = signToken(user.getId(), parsed.expiresAtEpoch(), user.getPasswordHash());
            if (!MessageDigest.isEqual(parsed.signature(), expectedSignature)) {
                throw new IllegalArgumentException("Invalid or already consumed reset token");
            }

            user.updatePassword(passwordHasher.hash(newPassword));
            return null;
        });
    }

    private String createToken(UUID userId, long expiresAtEpoch, String currentPasswordHash) {
        byte[] signature = signToken(userId, expiresAtEpoch, currentPasswordHash);
        String signatureB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        return userId + "." + expiresAtEpoch + "." + signatureB64;
    }

    private ParsedToken parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Reset token is required");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed reset token");
        }
        try {
            UUID userId = UUID.fromString(parts[0]);
            long expiresAtEpoch = Long.parseLong(parts[1]);
            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
            return new ParsedToken(userId, expiresAtEpoch, signature);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid reset token format", exception);
        }
    }

    private byte[] signToken(UUID userId, long expiresAtEpoch, String passwordHash) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            String payload = userId + ":" + expiresAtEpoch + ":" + passwordHash;
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Failed to calculate HMAC signature", exception);
        }
    }

    private record ParsedToken(UUID userId, long expiresAtEpoch, byte[] signature) {
    }

    private static byte[] resolveSecretKey() {
        String key = AppConfig.get("app.secret.key");
        if (key == null || key.isBlank()) {
            return "default-secret-key-for-ticketscenter-platform".getBytes(StandardCharsets.UTF_8);
        }
        return key.getBytes(StandardCharsets.UTF_8);
    }
}
