package vn.ticketscenter.settlement.model;

import jakarta.persistence.*;
import vn.ticketscenter.settlement.model.SettlementEnums;

@Entity
@Table(name = "tc_settlements", schema = "dbo")
public class Settlement {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private vn.ticketscenter.event.model.Event event;

    @Column(name = "gross_revenue", nullable = false, precision = 19, scale = 0)
    private java.math.BigDecimal grossRevenue;

    @Column(name = "total_refund", nullable = false, precision = 19, scale = 0)
    private java.math.BigDecimal totalRefund;

    @Column(name = "total_commission", nullable = false, precision = 19, scale = 0)
    private java.math.BigDecimal totalCommission;

    @Column(name = "net_payable", nullable = false, precision = 19, scale = 0)
    private java.math.BigDecimal netPayable;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SettlementEnums.SettlementStatus status;

    @Column(name = "confirmed_at", nullable = true)
    private java.time.Instant confirmedAt;

    @Column(name = "created_at", nullable = false)
    private java.time.Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Settlement() {
    }

    public Settlement(vn.ticketscenter.event.model.Event event, java.time.Instant createdAt) {
        this.event = java.util.Objects.requireNonNull(event);
        this.createdAt = java.util.Objects.requireNonNull(createdAt);
        this.grossRevenue = java.math.BigDecimal.ZERO;
        this.totalRefund = java.math.BigDecimal.ZERO;
        this.totalCommission = java.math.BigDecimal.ZERO;
        this.netPayable = java.math.BigDecimal.ZERO;
        this.status = SettlementEnums.SettlementStatus.DRAFT;
    }

    public java.util.UUID getId() {
        return id;
    }

    public void recalculate(java.math.BigDecimal grossRevenue,
                            java.math.BigDecimal totalRefund,
                            java.math.BigDecimal totalCommission) {
        if (status != SettlementEnums.SettlementStatus.DRAFT) {
            throw new IllegalStateException("only draft settlement can be recalculated");
        }
        java.math.BigDecimal net = requireAmounts(grossRevenue, totalRefund, totalCommission);
        this.grossRevenue = grossRevenue;
        this.totalRefund = totalRefund;
        this.totalCommission = totalCommission;
        this.netPayable = net;
    }

    public void confirm(java.time.Instant now) {
        if (status != SettlementEnums.SettlementStatus.DRAFT) {
            throw new IllegalStateException("only draft settlement can be confirmed");
        }
        confirmedAt = java.util.Objects.requireNonNull(now);
        status = netPayable.signum() == 0
                ? SettlementEnums.SettlementStatus.PAID
                : SettlementEnums.SettlementStatus.CONFIRMED;
    }

    public void markPaid() {
        if (status != SettlementEnums.SettlementStatus.CONFIRMED) {
            throw new IllegalStateException("only confirmed settlement can be marked paid");
        }
        status = SettlementEnums.SettlementStatus.PAID;
    }

    public SettlementEnums.SettlementStatus getStatus() {
        return status;
    }

    private static java.math.BigDecimal requireAmounts(java.math.BigDecimal grossRevenue,
                                                       java.math.BigDecimal totalRefund,
                                                       java.math.BigDecimal totalCommission) {
        if (grossRevenue == null || totalRefund == null || totalCommission == null
                || grossRevenue.signum() < 0 || totalRefund.signum() < 0 || totalCommission.signum() < 0
                || totalRefund.compareTo(grossRevenue) > 0
                || totalCommission.compareTo(grossRevenue.subtract(totalRefund)) > 0) {
            throw new IllegalArgumentException("settlement totals are inconsistent");
        }
        return grossRevenue.subtract(totalRefund).subtract(totalCommission);
    }
}
