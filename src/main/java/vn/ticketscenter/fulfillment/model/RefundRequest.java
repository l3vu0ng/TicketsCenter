package vn.ticketscenter.fulfillment.model;

import jakarta.persistence.*;
import vn.ticketscenter.fulfillment.model.FulfillmentEnums;

@Entity
@Table(name = "tc_refund_requests", schema = "dbo")
public class RefundRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private vn.ticketscenter.order.model.Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private vn.ticketscenter.identity.model.User requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "reviewer_id", nullable = true)
    private vn.ticketscenter.identity.model.User reviewer;

    @Column(name = "reason", nullable = false, length = 2000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false)
    private FulfillmentEnums.RefundRequestReason reasonType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FulfillmentEnums.RefundRequestStatus status;

    @Column(name = "requested_at", nullable = false)
    private java.time.Instant requestedAt;

    @Column(name = "decided_at", nullable = true)
    private java.time.Instant decidedAt;

    @Column(name = "rejection_reason", nullable = true, length = 1000)
    private String rejectionReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RefundRequest() {
    }

    public java.util.UUID getId() {
        return id;
    }
}
