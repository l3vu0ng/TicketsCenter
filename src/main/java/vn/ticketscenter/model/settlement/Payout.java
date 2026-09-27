package vn.ticketscenter.model.settlement;

import jakarta.persistence.*;
import vn.ticketscenter.model.ModelEnums;

@Entity
@Table(name = "tc_payouts", schema = "dbo")
public class Payout {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private vn.ticketscenter.model.settlement.Settlement settlement;

    @Column(name = "amount", nullable = false, precision = 19, scale = 0)
    private java.math.BigDecimal amount;

    @Column(name = "reference", nullable = false, length = 100)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ModelEnums.PayoutStatus status;

    @Column(name = "created_at", nullable = false)
    private java.time.Instant createdAt;

    @Column(name = "paid_at", nullable = true)
    private java.time.Instant paidAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Payout() {
    }

    public java.util.UUID getId() {
        return id;
    }
}
