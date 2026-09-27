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

    public java.util.UUID getId() {
        return id;
    }
}
