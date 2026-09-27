package vn.ticketscenter.order.model;

import jakarta.persistence.*;
import vn.ticketscenter.order.model.OrderEnums;

@Entity
@Table(name = "tc_payments", schema = "dbo")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private vn.ticketscenter.order.model.Order order;

    @Column(name = "txn_ref", nullable = false, length = 100)
    private String txnRef;

    @Column(name = "amount", nullable = false, precision = 19, scale = 0)
    private java.math.BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderEnums.PaymentStatus status;

    @Column(name = "provider_reference", nullable = true, length = 200)
    private String providerReference;

    @Column(name = "created_at", nullable = false)
    private java.time.Instant createdAt;

    @Column(name = "captured_at", nullable = true)
    private java.time.Instant capturedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Payment() {
    }

    public java.util.UUID getId() {
        return id;
    }
}
