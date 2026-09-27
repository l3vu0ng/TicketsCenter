package vn.ticketscenter.model.settlement;

import jakarta.persistence.*;
import vn.ticketscenter.model.ModelEnums;

@Entity
@Table(name = "tc_settlement_items", schema = "dbo")
public class SettlementItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private vn.ticketscenter.model.settlement.Settlement settlement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private vn.ticketscenter.model.order.Order order;

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
}
