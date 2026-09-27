package vn.ticketscenter.ticketing.model;

import jakarta.persistence.*;
import vn.ticketscenter.ticketing.model.TicketingEnums;

@Entity
@Table(name = "tc_ticket_hold_items", schema = "dbo")
public class TicketHoldItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hold_id", nullable = false)
    private vn.ticketscenter.ticketing.model.TicketHold hold;

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

    protected TicketHoldItem() {
    }

    public java.util.UUID getId() {
        return id;
    }
}
