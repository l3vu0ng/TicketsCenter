package vn.ticketscenter.order.model;

import jakarta.persistence.*;
import vn.ticketscenter.order.model.OrderEnums;

@Entity
@Table(name = "tc_order_items", schema = "dbo")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private vn.ticketscenter.order.model.Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private vn.ticketscenter.event.model.Zone zone;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "seat_id", nullable = true)
    private vn.ticketscenter.event.model.Seat seat;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 0)
    private java.math.BigDecimal unitPrice;

    @Column(name = "zone_name_snapshot", nullable = false, length = 120)
    private String zoneNameSnapshot;

    @Column(name = "seat_label_snapshot", nullable = true, length = 50)
    private String seatLabelSnapshot;

    protected OrderItem() {
    }

    public java.util.UUID getId() {
        return id;
    }
}
