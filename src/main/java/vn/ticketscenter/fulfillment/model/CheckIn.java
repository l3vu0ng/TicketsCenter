package vn.ticketscenter.fulfillment.model;

import jakarta.persistence.*;
import vn.ticketscenter.fulfillment.model.FulfillmentEnums;

@Entity
@Table(name = "tc_check_ins", schema = "dbo")
public class CheckIn {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private vn.ticketscenter.event.model.Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "ticket_id", nullable = true)
    private vn.ticketscenter.fulfillment.model.Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private vn.ticketscenter.identity.model.User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private FulfillmentEnums.CheckInResult result;

    @Column(name = "scanned_at", nullable = false)
    private java.time.Instant scannedAt;

    protected CheckIn() {
    }

    public java.util.UUID getId() {
        return id;
    }
}
