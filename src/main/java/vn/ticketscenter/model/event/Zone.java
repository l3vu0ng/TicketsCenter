package vn.ticketscenter.model.event;

import jakarta.persistence.*;
import vn.ticketscenter.model.ModelEnums;

@Entity
@Table(name = "tc_zones", schema = "dbo")
public class Zone {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private vn.ticketscenter.model.event.Event event;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ModelEnums.ZoneType type;

    @Column(name = "price", nullable = false, precision = 19, scale = 0)
    private java.math.BigDecimal price;

    @Column(name = "capacity", nullable = true)
    private Integer capacity;

    @Column(name = "held_quantity", nullable = false)
    private int heldQuantity;

    @Column(name = "sold_quantity", nullable = false)
    private int soldQuantity;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Zone() {
    }

    public Zone(Event event, String name, ModelEnums.ZoneType type,
                java.math.BigDecimal price, Integer capacity) {
        if (price.signum() < 0) {
            throw new IllegalArgumentException("price must not be negative");
        }
        if (type == ModelEnums.ZoneType.STANDING && (capacity == null || capacity <= 0)) {
            throw new IllegalArgumentException("standing capacity must be positive");
        }
        if (type == ModelEnums.ZoneType.SEATED && capacity != null) {
            throw new IllegalArgumentException("seated zone does not use capacity");
        }
        this.event = java.util.Objects.requireNonNull(event);
        this.name = java.util.Objects.requireNonNull(name);
        this.type = java.util.Objects.requireNonNull(type);
        this.price = price;
        this.capacity = capacity;
    }

    public java.util.UUID getId() {
        return id;
    }
}
