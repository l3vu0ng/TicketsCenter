package vn.ticketscenter.model.fulfillment;

import jakarta.persistence.*;
import vn.ticketscenter.model.ModelEnums;

@Entity
@Table(name = "tc_tickets", schema = "dbo")
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private vn.ticketscenter.model.order.OrderItem orderItem;

    @Column(name = "ticket_code", nullable = false, length = 100)
    private String ticketCode;

    @Column(name = "qr_secret_hash", nullable = false, length = 64)
    private byte[] qrSecretHash;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 0)
    private java.math.BigDecimal paidAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ModelEnums.TicketStatus status;

    @Column(name = "issued_at", nullable = false)
    private java.time.Instant issuedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Ticket() {
    }

    public java.util.UUID getId() {
        return id;
    }
}
