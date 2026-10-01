package vn.ticketscenter.settlement.model;

import jakarta.persistence.*;
import vn.ticketscenter.settlement.model.SettlementEnums;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "tc_commission_rules", schema = "dbo")
public class CommissionRule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private vn.ticketscenter.identity.model.Organization organization;

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

    public BigDecimal calculateFee(BigDecimal remainingAmount) {
        return calculateFee(remainingAmount, ratePercent, fixedFee);
    }

    public static BigDecimal calculateFee(BigDecimal remainingAmount,
                                          BigDecimal ratePercent,
                                          BigDecimal fixedFee) {
        if (remainingAmount == null || ratePercent == null || fixedFee == null
                || remainingAmount.signum() < 0 || ratePercent.signum() < 0 || fixedFee.signum() < 0) {
            throw new IllegalArgumentException("commission inputs must be non-negative");
        }
        if (remainingAmount.signum() == 0) return BigDecimal.ZERO;
        BigDecimal fee = remainingAmount.multiply(ratePercent)
                .divide(new BigDecimal("100"))
                .add(fixedFee)
                .setScale(0, RoundingMode.HALF_UP);
        return fee.min(remainingAmount);
    }
}
