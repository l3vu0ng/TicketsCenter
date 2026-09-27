package vn.ticketscenter.model.ticketing;

import jakarta.persistence.*;
import vn.ticketscenter.model.ModelEnums;

@Entity
@Table(name = "tc_ticket_holds", schema = "dbo")
public class TicketHold {
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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ModelEnums.TicketHoldStatus status;

    @Column(name = "created_at", nullable = false)
    private java.time.Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private java.time.Instant expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected TicketHold() {
    }

    public java.util.UUID getId() {
        return id;
    }
}
