package vn.ticketscenter.model.settlement;

import jakarta.persistence.*;
import vn.ticketscenter.model.ModelEnums;

@Entity
@Table(name = "tc_commission_rules", schema = "dbo")
public class CommissionRule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private vn.ticketscenter.model.identity.Organization organization;

    @Column(name = "rate_percent", nullable = false, precision = 7, scale = 4)
    private java.math.BigDecimal ratePercent;

    @Column(name = "fixed_fee", nullable = false, precision = 19, scale = 0)
    private java.math.BigDecimal fixedFee;

    @Column(name = "effective_from", nullable = false)
    private java.time.Instant effectiveFrom;

    @Column(name = "effective_to", nullable = false)
    private java.time.Instant effectiveTo;

    protected CommissionRule() {
    }

    public java.util.UUID getId() {
        return id;
    }
}
