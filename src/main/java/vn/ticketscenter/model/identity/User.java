package vn.ticketscenter.model.identity;

import jakarta.persistence.*;
import vn.ticketscenter.model.ModelEnums;

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
    private ModelEnums.UserStatus status;

    @Column(name = "email_verified_at", nullable = true)
    private java.time.Instant emailVerifiedAt;

    @Column(name = "auth_version", nullable = false)
    private int authVersion;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private java.time.Instant createdAt;

    protected User() {
    }

    public java.util.UUID getId() {
        return id;
    }
}
