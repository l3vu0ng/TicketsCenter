package vn.ticketscenter.model.order;

import jakarta.persistence.*;
import vn.ticketscenter.model.ModelEnums;

@Entity
@Table(name = "tc_coupons", schema = "dbo")
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private vn.ticketscenter.model.identity.Organization organization;

    @Column(name = "code", nullable = false, length = 80)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private ModelEnums.DiscountType discountType;

    @Column(name = "percentage_value", nullable = true, precision = 5, scale = 2)
    private java.math.BigDecimal percentageValue;

    @Column(name = "fixed_amount", nullable = true, precision = 19, scale = 0)
    private java.math.BigDecimal fixedAmount;

    @Column(name = "max_discount_amount", nullable = true, precision = 19, scale = 0)
    private java.math.BigDecimal maxDiscountAmount;

    @Column(name = "max_uses", nullable = false)
    private int maxUses;

    @Column(name = "valid_from", nullable = false)
    private java.time.Instant validFrom;

    @Column(name = "valid_to", nullable = false)
    private java.time.Instant validTo;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Coupon() {
    }

    public java.util.UUID getId() {
        return id;
    }
}
