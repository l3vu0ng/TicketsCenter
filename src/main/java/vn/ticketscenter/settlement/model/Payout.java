package vn.ticketscenter.settlement.model;

import jakarta.persistence.*;
import vn.ticketscenter.settlement.model.SettlementEnums;

@Entity
@Table(name = "tc_payouts", schema = "dbo")
public class Payout {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private vn.ticketscenter.settlement.model.Settlement settlement;

    @Column(name = "amount", nullable = false, precision = 19, scale = 0)
    private java.math.BigDecimal amount;

    @Column(name = "reference", nullable = false, length = 100)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SettlementEnums.PayoutStatus status;

    @Column(name = "created_at", nullable = false)
    private java.time.Instant createdAt;

    @Column(name = "paid_at", nullable = true)
    private java.time.Instant paidAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Payout() {
    }

    public Payout(Settlement settlement, java.math.BigDecimal amount,
                  String reference, java.time.Instant createdAt) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 0) {
            throw new IllegalArgumentException("payout amount must be positive whole VND");
        }
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("payout reference is required");
        }
        this.settlement = java.util.Objects.requireNonNull(settlement);
        this.amount = amount;
        this.reference = reference;
        this.createdAt = java.util.Objects.requireNonNull(createdAt);
        this.status = SettlementEnums.PayoutStatus.PENDING;
    }

    public java.util.UUID getId() {
        return id;
    }

    public void markSucceeded(java.time.Instant now) {
        requirePending();
        java.time.Instant paidAt = java.util.Objects.requireNonNull(now);
        status = SettlementEnums.PayoutStatus.SUCCEEDED;
        this.paidAt = paidAt;
    }

    public void markFailed() {
        requirePending();
        status = SettlementEnums.PayoutStatus.FAILED;
        paidAt = null;
    }

    public SettlementEnums.PayoutStatus getStatus() {
        return status;
    }

    private void requirePending() {
        if (status != SettlementEnums.PayoutStatus.PENDING) {
            throw new IllegalStateException("only pending payout can change outcome");
        }
    }
}
