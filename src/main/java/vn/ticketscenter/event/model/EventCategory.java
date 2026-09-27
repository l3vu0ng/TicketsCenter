package vn.ticketscenter.event.model;

import jakarta.persistence.*;
import vn.ticketscenter.event.model.EventEnums;

@Entity
@Table(name = "tc_event_categories", schema = "dbo")
public class EventCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "slug", nullable = false, length = 120)
    private String slug;

    protected EventCategory() {
    }

    public EventCategory(String name, String slug) {
        this.name = java.util.Objects.requireNonNull(name);
        this.slug = java.util.Objects.requireNonNull(slug);
    }

    public java.util.UUID getId() {
        return id;
    }
}
