package vn.ticketscenter.identity.model;

import jakarta.persistence.*;
import vn.ticketscenter.identity.model.IdentityEnums;

@Entity
@Table(name = "tc_organization_memberships", schema = "dbo")
public class OrganizationMembership {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private vn.ticketscenter.identity.model.User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private vn.ticketscenter.identity.model.Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private IdentityEnums.OrganizationRole role;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "joined_at", nullable = false)
    private java.time.Instant joinedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected OrganizationMembership() {
    }

    public java.util.UUID getId() {
        return id;
    }
}
