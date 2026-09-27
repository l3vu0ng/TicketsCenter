package vn.ticketscenter.model.event;

import jakarta.persistence.*;
import vn.ticketscenter.model.ModelEnums;

@Entity
@Table(name = "tc_seats", schema = "dbo")
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private vn.ticketscenter.model.event.Zone zone;

    @Column(name = "row_name", nullable = false, length = 20)
    private String rowName;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    @Column(name = "label", nullable = false, length = 50)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ModelEnums.SeatStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Seat() {
    }

    public java.util.UUID getId() {
        return id;
    }
}
