package vn.ticketscenter.order.model;

import jakarta.persistence.*;
import vn.ticketscenter.order.model.OrderEnums;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tc_coupons", schema = "dbo")
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private vn.ticketscenter.identity.model.Organization organization;

    @Column(name = "code", nullable = false, length = 80)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private OrderEnums.DiscountType discountType;

    @Column(name = "percentage_value", nullable = true, precision = 5, scale = 2)
    private BigDecimal percentageValue;

    @Column(name = "fixed_amount", nullable = true, precision = 19, scale = 0)
    private BigDecimal fixedAmount;

    @Column(name = "max_discount_amount", nullable = true, precision = 19, scale = 0)
    private BigDecimal maxDiscountAmount;

    @Column(name = "max_uses", nullable = false)
    private int maxUses;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to", nullable = false)
    private Instant validTo;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Coupon() {
    }

    public Coupon(UUID id, vn.ticketscenter.identity.model.Organization organization, String code,
                  OrderEnums.DiscountType discountType, BigDecimal percentageValue, BigDecimal fixedAmount,
                  BigDecimal maxDiscountAmount, int maxUses, Instant validFrom, Instant validTo, boolean active) {
        this.id = id;
        this.organization = organization;
        this.code = code;
        this.discountType = discountType;
        this.percentageValue = percentageValue;
        this.fixedAmount = fixedAmount;
        this.maxDiscountAmount = maxDiscountAmount;
        this.maxUses = maxUses;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public vn.ticketscenter.identity.model.Organization getOrganization() {
        return organization;
    }

    public String getCode() {
        return code;
    }

    public OrderEnums.DiscountType getDiscountType() {
        return discountType;
    }

    public BigDecimal getPercentageValue() {
        return percentageValue;
    }

    public BigDecimal getFixedAmount() {
        return fixedAmount;
    }

    public BigDecimal getMaxDiscountAmount() {
        return maxDiscountAmount;
    }

    public int getMaxUses() {
        return maxUses;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
