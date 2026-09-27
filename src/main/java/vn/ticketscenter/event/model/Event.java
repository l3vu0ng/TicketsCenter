package vn.ticketscenter.event.model;

import jakarta.persistence.*;
import vn.ticketscenter.event.model.EventEnums;

@Entity
@Table(name = "tc_events", schema = "dbo")
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private vn.ticketscenter.identity.model.Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private vn.ticketscenter.event.model.EventCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "commission_rule_id", nullable = true)
    private vn.ticketscenter.settlement.model.CommissionRule commissionRule;

    @Column(name = "title", nullable = false, length = 250)
    private String title;

    @Column(name = "description", nullable = true)
    private String description;

    @Column(name = "cover_image_url", nullable = true, length = 2048)
    private String coverImageUrl;

    @Column(name = "venue_name", nullable = false, length = 250)
    private String venueName;

    @Column(name = "venue_address", nullable = false, length = 500)
    private String venueAddress;

    @Column(name = "sale_start", nullable = false)
    private java.time.Instant saleStart;

    @Column(name = "sale_end", nullable = false)
    private java.time.Instant saleEnd;

    @Column(name = "start_time", nullable = false)
    private java.time.Instant startTime;

    @Column(name = "end_time", nullable = false)
    private java.time.Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EventEnums.EventStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private java.time.Instant createdAt;

    protected Event() {
    }

    public Event(vn.ticketscenter.identity.model.Organization organization, EventCategory category, String title,
                 String venueName, String venueAddress, java.time.Instant saleStart,
                 java.time.Instant saleEnd, java.time.Instant startTime, java.time.Instant endTime) {
        if (!saleStart.isBefore(saleEnd) || saleEnd.isAfter(startTime) || !startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("saleStart < saleEnd <= startTime < endTime");
        }
        this.organization = java.util.Objects.requireNonNull(organization);
        this.category = java.util.Objects.requireNonNull(category);
        this.title = java.util.Objects.requireNonNull(title);
        this.venueName = java.util.Objects.requireNonNull(venueName);
        this.venueAddress = java.util.Objects.requireNonNull(venueAddress);
        this.saleStart = saleStart;
        this.saleEnd = saleEnd;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = EventEnums.EventStatus.DRAFT;
        this.createdAt = java.time.Instant.now();
    }

    public java.util.UUID getId() {
        return id;
    }
}
