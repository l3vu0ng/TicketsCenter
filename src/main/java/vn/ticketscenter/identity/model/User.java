package vn.ticketscenter.identity.model;

import jakarta.persistence.*;
import vn.ticketscenter.identity.model.IdentityEnums;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "tc_users", schema = "dbo")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "normalized_email", nullable = false, length = 320)
    private String normalizedEmail;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "phone", nullable = true, length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IdentityEnums.UserStatus status;

    @Column(name = "email_verified_at", nullable = true)
    private java.time.Instant emailVerifiedAt;

    @Column(name = "auth_version", nullable = false)
    private int authVersion;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private java.time.Instant createdAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tc_user_platform_roles", schema = "dbo", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 30)
    private Set<String> platformRoles = new HashSet<>();

    protected User() {
    }

    public User(String email, String normalizedEmail, String passwordHash, String fullName, Instant createdAt) {
        this(null, email, normalizedEmail, passwordHash, fullName, createdAt);
    }

    public User(UUID id, String email, String normalizedEmail, String passwordHash, String fullName, Instant createdAt) {
        this.id = id;
        this.email = java.util.Objects.requireNonNull(email);
        this.normalizedEmail = java.util.Objects.requireNonNull(normalizedEmail);
        this.passwordHash = java.util.Objects.requireNonNull(passwordHash);
        this.fullName = java.util.Objects.requireNonNull(fullName);
        this.status = IdentityEnums.UserStatus.ACTIVE;
        this.createdAt = java.util.Objects.requireNonNull(createdAt);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() { return email; }
    public String getNormalizedEmail() { return normalizedEmail; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public IdentityEnums.UserStatus getStatus() { return status; }
    public int getAuthVersion() { return authVersion; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public boolean isActive() { return status == IdentityEnums.UserStatus.ACTIVE; }
    public boolean hasPlatformRole(String role) { return platformRoles.contains(role); }
    public void grantPlatformRole(String role) { platformRoles.add(java.util.Objects.requireNonNull(role)); }
    public void verifyEmail(Instant verifiedAt) { this.emailVerifiedAt = java.util.Objects.requireNonNull(verifiedAt); }
    public void updatePassword(String newPasswordHash) {
        this.passwordHash = java.util.Objects.requireNonNull(newPasswordHash);
        this.authVersion++;
    }
    public void setStatus(IdentityEnums.UserStatus status) {
        this.status = java.util.Objects.requireNonNull(status);
    }
}
