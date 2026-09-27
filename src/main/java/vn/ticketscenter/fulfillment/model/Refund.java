package vn.ticketscenter.fulfillment.model;

import jakarta.persistence.*;
import vn.ticketscenter.fulfillment.model.FulfillmentEnums;

@Entity
@Table(name = "tc_refunds", schema = "dbo")
public class Refund {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "refund_request_id", nullable = true)
    private vn.ticketscenter.fulfillment.model.RefundRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private vn.ticketscenter.order.model.Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false)
    private FulfillmentEnums.RefundPurpose purpose;

    @Column(name = "amount", nullable = false, precision = 19, scale = 0)
    private java.math.BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FulfillmentEnums.RefundStatus status;

    @Column(name = "provider_reference", nullable = true, length = 200)
    private String providerReference;

    @Column(name = "created_at", nullable = false)
    private java.time.Instant createdAt;

    @Column(name = "processed_at", nullable = true)
    private java.time.Instant processedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Refund() {
    }

    public java.util.UUID getId() {
        return id;
    }
}
