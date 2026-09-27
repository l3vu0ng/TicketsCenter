package vn.ticketscenter.model.order;

import jakarta.persistence.*;
import vn.ticketscenter.model.ModelEnums;

@Entity
@Table(name = "tc_orders", schema = "dbo")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private vn.ticketscenter.model.identity.User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private vn.ticketscenter.model.event.Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hold_id", nullable = false)
    private vn.ticketscenter.model.ticketing.TicketHold hold;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "coupon_id", nullable = true)
    private vn.ticketscenter.model.order.Coupon coupon;

    @Column(name = "order_code", nullable = false, length = 64)
    private String orderCode;

    @Column(name = "subtotal_amount", nullable = false, precision = 19, scale = 0)
    private java.math.BigDecimal subtotalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 0)
    private java.math.BigDecimal discountAmount;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 0)
    private java.math.BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ModelEnums.OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private java.time.Instant createdAt;

    @Column(name = "paid_at", nullable = true)
    private java.time.Instant paidAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Order() {
    }

    public java.util.UUID getId() {
        return id;
    }
}
