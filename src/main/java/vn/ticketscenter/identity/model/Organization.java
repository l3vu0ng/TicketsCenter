package vn.ticketscenter.identity.model;

import jakarta.persistence.*;
import vn.ticketscenter.identity.model.IdentityEnums;

@Entity
@Table(name = "tc_organizations", schema = "dbo")
public class Organization {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "contact_email", nullable = false, length = 320)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = true, length = 30)
    private String contactPhone;

    @Column(name = "description", nullable = true, length = 2000)
    private String description;

    @Column(name = "created_at", nullable = false)
    private java.time.Instant createdAt;

    protected Organization() {
    }

    public Organization(String name, String contactEmail, java.time.Instant createdAt) {
        this.name = java.util.Objects.requireNonNull(name);
        this.contactEmail = java.util.Objects.requireNonNull(contactEmail);
        this.createdAt = java.util.Objects.requireNonNull(createdAt);
    }

    public java.util.UUID getId() {
        return id;
    }
}
