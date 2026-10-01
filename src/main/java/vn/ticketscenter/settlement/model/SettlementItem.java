package vn.ticketscenter.settlement.model;

import jakarta.persistence.*;
import vn.ticketscenter.settlement.model.SettlementEnums;

@Entity
@Table(name = "tc_settlement_items", schema = "dbo")
public class SettlementItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private vn.ticketscenter.settlement.model.Settlement settlement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private vn.ticketscenter.order.model.Order order;

    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 0)
    private java.math.BigDecimal grossAmount;

    @Column(name = "refund_amount", nullable = false, precision = 19, scale = 0)
    private java.math.BigDecimal refundAmount;

    @Column(name = "commission_amount", nullable = false, precision = 19, scale = 0)
    private java.math.BigDecimal commissionAmount;

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 0)
    private java.math.BigDecimal netAmount;

    protected SettlementItem() {
    }

    public java.util.UUID getId() {
        return id;
    }

    public void validateAmounts() {
        validateAmounts(grossAmount, refundAmount, commissionAmount, netAmount);
    }

    public static void validateAmounts(java.math.BigDecimal grossAmount,
                                       java.math.BigDecimal refundAmount,
                                       java.math.BigDecimal commissionAmount,
                                       java.math.BigDecimal netAmount) {
        if (grossAmount == null || refundAmount == null || commissionAmount == null || netAmount == null
                || grossAmount.signum() < 0 || refundAmount.signum() < 0
                || commissionAmount.signum() < 0 || netAmount.signum() < 0
                || refundAmount.compareTo(grossAmount) > 0
                || commissionAmount.compareTo(grossAmount.subtract(refundAmount)) > 0
                || netAmount.compareTo(grossAmount.subtract(refundAmount).subtract(commissionAmount)) != 0) {
            throw new IllegalArgumentException("settlement item amounts are inconsistent");
        }
    }
}
