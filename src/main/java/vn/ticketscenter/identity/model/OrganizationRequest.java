package vn.ticketscenter.identity.model;

import jakarta.persistence.*;
import vn.ticketscenter.identity.model.IdentityEnums;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tc_organization_requests", schema = "dbo")
public class OrganizationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private User applicant;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "reviewer_id", nullable = true)
    private User reviewer;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "organization_id", nullable = true)
    private Organization organization;

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
    private IdentityEnums.OrganizationRequestStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_at", nullable = true)
    private Instant decidedAt;

    @Column(name = "rejection_reason", nullable = true, length = 1000)
    private String rejectionReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected OrganizationRequest() {
    }

    public UUID getId() {
        return id;
    }

    public User getApplicant() {
        return applicant;
    }

    public User getReviewer() {
        return reviewer;
    }

    public void setReviewer(User reviewer) {
        this.reviewer = reviewer;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public String getName() {
        return name;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getDescription() {
        return description;
    }

    public IdentityEnums.OrganizationRequestStatus getStatus() {
        return status;
    }

    public void setStatus(IdentityEnums.OrganizationRequestStatus status) {
        this.status = status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public long getVersion() {
        return version;
    }
}
