package vn.ticketscenter.model.identity;

import jakarta.persistence.*;
import vn.ticketscenter.model.ModelEnums;

@Entity
@Table(name = "tc_organization_requests", schema = "dbo")
public class OrganizationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private vn.ticketscenter.model.identity.User applicant;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "reviewer_id", nullable = true)
    private vn.ticketscenter.model.identity.User reviewer;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "organization_id", nullable = true)
    private vn.ticketscenter.model.identity.Organization organization;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "contact_email", nullable = false, length = 320)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = true, length = 30)
    private String contactPhone;

    @Column(name = "description", nullable = true, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ModelEnums.OrganizationRequestStatus status;

    @Column(name = "requested_at", nullable = false)
    private java.time.Instant requestedAt;

    @Column(name = "decided_at", nullable = true)
    private java.time.Instant decidedAt;

    @Column(name = "rejection_reason", nullable = true, length = 1000)
    private String rejectionReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected OrganizationRequest() {
    }

    public java.util.UUID getId() {
        return id;
    }
}
