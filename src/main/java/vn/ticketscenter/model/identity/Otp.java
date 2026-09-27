package vn.ticketscenter.model.identity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "tc_otps", schema = "dbo")
public class Otp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = true)
    private UUID userId;

    @Column(name = "email_normalized", nullable = false, length = 320)
    private String emailNormalized;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private OtpPurpose purpose;

    @Column(name = "secret_hash", nullable = false, length = 64)
    private byte[] secretHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private short failedAttempts;

    @Column(name = "consumed_at", nullable = true)
    private Instant consumedAt;

    @Column(name = "invalidated_at", nullable = true)
    private Instant invalidatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Otp() {
    }

    public Otp(UUID userId, String emailNormalized, OtpPurpose purpose, byte[] secretHash, Instant expiresAt, Instant createdAt) {
        this.userId = userId;
        this.emailNormalized = Objects.requireNonNull(emailNormalized);
        this.purpose = Objects.requireNonNull(purpose);
        this.secretHash = Objects.requireNonNull(secretHash);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.failedAttempts = 0;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public OtpPurpose getPurpose() {
        return purpose;
    }

    public byte[] getSecretHash() {
        return secretHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public short getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public Instant getInvalidatedAt() {
        return invalidatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isUsable(Instant now) {
        return consumedAt == null
                && invalidatedAt == null
                && failedAttempts < 5
                && now.isBefore(expiresAt);
    }

    public void recordFailedAttempt(Instant now) {
        this.failedAttempts++;
        if (this.failedAttempts >= 5) {
            this.invalidatedAt = Objects.requireNonNull(now);
        }
    }

    public void consume(Instant now) {
        this.consumedAt = Objects.requireNonNull(now);
    }

    public void invalidate(Instant now) {
        this.invalidatedAt = Objects.requireNonNull(now);
    }
}
