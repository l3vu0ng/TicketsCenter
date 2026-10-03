package vn.ticketscenter.event.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.Query;
import vn.ticketscenter.event.dto.EventDtos.CreateEventCommand;
import vn.ticketscenter.event.dto.EventDtos.EventPage;
import vn.ticketscenter.event.dto.EventDtos.EventSearch;
import vn.ticketscenter.event.dto.EventDtos.EventView;
import vn.ticketscenter.event.dto.EventDtos.SeatSpec;
import vn.ticketscenter.event.dto.EventDtos.SeatSummaryDto;
import vn.ticketscenter.event.dto.EventDtos.ZoneCommand;
import vn.ticketscenter.event.dto.EventDtos.ZoneView;
import vn.ticketscenter.event.model.EventEnums;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

public class EventRepository {

    public UUID createDraft(EntityManager entityManager, UUID actorId, UUID organizationId, CreateEventCommand command) {
        requireManager(entityManager, actorId, organizationId);
        if (((Number) entityManager.createNativeQuery(
                "SELECT COUNT_BIG(*) FROM dbo.tc_event_categories WHERE id = :id")
                .setParameter("id", command.categoryId()).getSingleResult()).longValue() == 0) {
            throw new NoSuchElementException("event category not found");
        }
        UUID id = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT dbo.tc_events(id, organization_id, category_id, title, description,
                            venue_name, venue_address, sale_start, sale_end, start_time, end_time)
                        VALUES (:id, :organizationId, :categoryId, :title, :description,
                            :venueName, :venueAddress, :saleStart, :saleEnd, :startTime, :endTime)
                        """)
                .setParameter("id", id).setParameter("organizationId", organizationId)
                .setParameter("categoryId", command.categoryId()).setParameter("title", command.title())
                .setParameter("description", command.description()).setParameter("venueName", command.venueName())
                .setParameter("venueAddress", command.venueAddress()).setParameter("saleStart", command.saleStart())
                .setParameter("saleEnd", command.saleEnd()).setParameter("startTime", command.startTime())
                .setParameter("endTime", command.endTime()).executeUpdate();
        return id;
    }

    public UUID addZone(EntityManager entityManager, UUID actorId, UUID eventId,
                        ZoneCommand command, List<SeatSpec> seats) {
        UUID organizationId = requireEditableEvent(entityManager, actorId, eventId);
        UUID zoneId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT dbo.tc_zones(id, event_id, name, type, price, capacity)
                        VALUES (:id, :eventId, :name, :type, :price, :capacity)
                        """)
                .setParameter("id", zoneId).setParameter("eventId", eventId)
                .setParameter("name", command.name()).setParameter("type", command.type())
                .setParameter("price", command.price()).setParameter("capacity", command.capacity())
                .executeUpdate();
        for (SeatSpec seat : seats) {
            entityManager.createNativeQuery("""
                            INSERT dbo.tc_seats(id, zone_id, row_name, seat_number, label)
                            VALUES (:id, :zoneId, :rowName, :seatNumber, :label)
                            """)
                    .setParameter("id", UUID.randomUUID()).setParameter("zoneId", zoneId)
                    .setParameter("rowName", seat.rowName()).setParameter("seatNumber", seat.seatNumber())
                    .setParameter("label", seat.label()).executeUpdate();
        }
        return zoneId;
    }

    public void updateDraft(EntityManager entityManager, UUID actorId, UUID eventId, CreateEventCommand command) {
        requireEditableEvent(entityManager, actorId, eventId);
        if (((Number) entityManager.createNativeQuery("SELECT COUNT_BIG(*) FROM dbo.tc_event_categories WHERE id = :id")
                .setParameter("id", command.categoryId()).getSingleResult()).longValue() == 0) {
            throw new NoSuchElementException("event category not found");
        }
        entityManager.createNativeQuery("""
                        UPDATE dbo.tc_events SET category_id = :categoryId, title = :title, description = :description,
                            venue_name = :venueName, venue_address = :venueAddress, sale_start = :saleStart,
                            sale_end = :saleEnd, start_time = :startTime, end_time = :endTime, version = version + 1
                        WHERE id = :eventId
                        """)
                .setParameter("categoryId", command.categoryId()).setParameter("title", command.title())
                .setParameter("description", command.description()).setParameter("venueName", command.venueName())
                .setParameter("venueAddress", command.venueAddress()).setParameter("saleStart", command.saleStart())
                .setParameter("saleEnd", command.saleEnd()).setParameter("startTime", command.startTime())
                .setParameter("endTime", command.endTime()).setParameter("eventId", eventId).executeUpdate();
    }

    public void deleteDraft(EntityManager entityManager, UUID actorId, UUID eventId) {
        requireEditableEvent(entityManager, actorId, eventId);
        entityManager.createNativeQuery("DELETE s FROM dbo.tc_seats s JOIN dbo.tc_zones z ON z.id = s.zone_id WHERE z.event_id = :eventId")
                .setParameter("eventId", eventId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM dbo.tc_zones WHERE event_id = :eventId")
                .setParameter("eventId", eventId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM dbo.tc_events WHERE id = :eventId")
                .setParameter("eventId", eventId).executeUpdate();
    }

    public void replaceZone(EntityManager entityManager, UUID actorId, UUID zoneId,
                            ZoneCommand command, List<SeatSpec> seats) {
        UUID eventId = editableZoneEvent(entityManager, actorId, zoneId);
        entityManager.createNativeQuery("DELETE FROM dbo.tc_seats WHERE zone_id = :zoneId")
                .setParameter("zoneId", zoneId).executeUpdate();
        entityManager.createNativeQuery("""
                        UPDATE dbo.tc_zones SET name = :name, type = :type, price = :price, capacity = :capacity,
                            held_quantity = 0, sold_quantity = 0, version = version + 1 WHERE id = :zoneId
                        """)
                .setParameter("name", command.name()).setParameter("type", command.type())
                .setParameter("price", command.price()).setParameter("capacity", command.capacity())
                .setParameter("zoneId", zoneId).executeUpdate();
        for (SeatSpec seat : seats) {
            entityManager.createNativeQuery("""
                            INSERT dbo.tc_seats(id, zone_id, row_name, seat_number, label)
                            VALUES (:id, :zoneId, :rowName, :seatNumber, :label)
                            """)
                    .setParameter("id", UUID.randomUUID()).setParameter("zoneId", zoneId)
                    .setParameter("rowName", seat.rowName()).setParameter("seatNumber", seat.seatNumber())
                    .setParameter("label", seat.label()).executeUpdate();
        }
    }

    public void deleteZone(EntityManager entityManager, UUID actorId, UUID zoneId) {
        editableZoneEvent(entityManager, actorId, zoneId);
        entityManager.createNativeQuery("DELETE FROM dbo.tc_seats WHERE zone_id = :zoneId")
                .setParameter("zoneId", zoneId).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM dbo.tc_zones WHERE id = :zoneId")
                .setParameter("zoneId", zoneId).executeUpdate();
    }

    public void submit(EntityManager entityManager, UUID actorId, UUID eventId) {
        requireEditableEvent(entityManager, actorId, eventId);
        setActor(entityManager, actorId);
        int updated = entityManager.createNativeQuery("""
                        UPDATE dbo.tc_events SET status = 'PENDING_APPROVAL', rejection_reason = NULL,
                            version = version + 1
                        WHERE id = :eventId AND status IN ('DRAFT', 'REJECTED')
                          AND cover_image_url IS NOT NULL
                          AND EXISTS (SELECT 1 FROM dbo.tc_zones WHERE event_id = :eventId)
                          AND NOT EXISTS (
                              SELECT 1 FROM dbo.tc_zones z WHERE z.event_id = :eventId AND z.type = 'SEATED'
                                AND NOT EXISTS (SELECT 1 FROM dbo.tc_seats s WHERE s.zone_id = z.id))
                        """)
                .setParameter("eventId", eventId).executeUpdate();
        if (updated == 0) throw new IllegalStateException("event requires a cover image and valid zone layout");
    }

    public void publish(EntityManager entityManager, UUID eventId, UUID actorId, UUID commissionRuleId) {
        setActor(entityManager, actorId);
        entityManager.createStoredProcedureQuery("dbo.usp_PublishEvent")
                .registerStoredProcedureParameter("event_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("actor_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("commission_rule_id", UUID.class, ParameterMode.IN)
                .setParameter("event_id", eventId).setParameter("actor_id", actorId)
                .setParameter("commission_rule_id", commissionRuleId).execute();
    }

    public void reject(EntityManager entityManager, UUID eventId, UUID actorId, String reason) {
        setActor(entityManager, actorId);
        int updated = entityManager.createNativeQuery("""
                        UPDATE dbo.tc_events WITH (UPDLOCK, HOLDLOCK)
                        SET status = 'REJECTED', rejection_reason = :reason, version = version + 1
                        WHERE id = :eventId AND status = 'PENDING_APPROVAL'
                        """)
                .setParameter("reason", reason).setParameter("eventId", eventId).executeUpdate();
        if (updated == 0) throw new IllegalStateException("event is not pending approval");
    }

    public void requireEditable(EntityManager entityManager, UUID actorId, UUID eventId) {
        requireEditableEvent(entityManager, actorId, eventId);
    }

    public String replaceCover(EntityManager entityManager, UUID actorId, UUID eventId, String url) {
        requireEditableEvent(entityManager, actorId, eventId);
        @SuppressWarnings("unchecked") List<String> old = entityManager.createNativeQuery(
                        "SELECT cover_image_url FROM dbo.tc_events WHERE id = :eventId", String.class)
                .setParameter("eventId", eventId).getResultList();
        entityManager.createNativeQuery("""
                        UPDATE dbo.tc_events SET cover_image_url = :url, version = version + 1
                        WHERE id = :eventId AND status IN ('DRAFT', 'REJECTED')
                        """)
                .setParameter("url", url).setParameter("eventId", eventId).executeUpdate();
        return old.isEmpty() ? null : old.getFirst();
    }

    public EventPage search(EntityManager entityManager, EventSearch search) {
        String filters = "";
        if (search.query() != null) filters += " AND e.title LIKE :query";
        if (search.categoryId() != null) filters += " AND e.category_id = :categoryId";
        if (search.from() != null) filters += " AND e.start_time >= :from";
        if (search.to() != null) filters += " AND e.start_time < :to";
        String order = "minPrice".equals(search.sort())
                ? "e.minimum_price, e.start_time, e.event_id" : "e.start_time, e.event_id";
        Query rows = entityManager.createNativeQuery("""
                SELECT e.event_id, e.organization_id, e.category_id, c.name, e.title, e.description,
                       e.cover_image_url, e.venue_name, e.venue_address, e.sale_start, e.sale_end,
                       e.start_time, e.end_time, CAST('PUBLISHED' AS varchar(30)),
                       CAST(NULL AS nvarchar(1000)), e.minimum_price
                FROM dbo.vw_PublicEvents e JOIN dbo.tc_event_categories c ON c.id = e.category_id
                WHERE 1 = 1
                """ + filters + " ORDER BY " + order + " OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY");
        Query count = entityManager.createNativeQuery(
                "SELECT COUNT_BIG(*) FROM dbo.vw_PublicEvents e WHERE 1 = 1" + filters);
        bindSearch(rows, search);
        bindSearch(count, search);
        rows.setParameter("offset", (search.page() - 1) * search.pageSize()).setParameter("limit", search.pageSize());
        @SuppressWarnings("unchecked") List<Object[]> values = rows.getResultList();
        return new EventPage(values.stream().map(this::eventView).toList(), search.page(), search.pageSize(),
                ((Number) count.getSingleResult()).longValue());
    }

    public EventPage findForOrganization(EntityManager entityManager, UUID actorId, UUID organizationId,
                                         int page, int pageSize) {
        requireManager(entityManager, actorId, organizationId);
        @SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT e.id, e.organization_id, e.category_id, c.name, e.title, e.description,
                               e.cover_image_url, e.venue_name, e.venue_address, e.sale_start, e.sale_end,
                               e.start_time, e.end_time, e.status, e.rejection_reason, MIN(z.price)
                        FROM dbo.tc_events e JOIN dbo.tc_event_categories c ON c.id = e.category_id
                        LEFT JOIN dbo.tc_zones z ON z.event_id = e.id
                        WHERE e.organization_id = :organizationId
                        GROUP BY e.id, e.organization_id, e.category_id, c.name, e.title, e.description,
                                 e.cover_image_url, e.venue_name, e.venue_address, e.sale_start, e.sale_end,
                                 e.start_time, e.end_time, e.status, e.rejection_reason
                        ORDER BY e.created_at DESC, e.id
                        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
                        """)
                .setParameter("organizationId", organizationId).setParameter("offset", (page - 1) * pageSize)
                .setParameter("limit", pageSize).getResultList();
        long total = ((Number) entityManager.createNativeQuery(
                        "SELECT COUNT_BIG(*) FROM dbo.tc_events WHERE organization_id = :organizationId")
                .setParameter("organizationId", organizationId).getSingleResult()).longValue();
        return new EventPage(rows.stream().map(this::eventView).toList(), page, pageSize, total);
    }

    public EventView findPublic(EntityManager entityManager, UUID eventId) {
        @SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT e.event_id, e.organization_id, e.category_id, c.name, e.title, e.description,
                               e.cover_image_url, e.venue_name, e.venue_address, e.sale_start, e.sale_end,
                               e.start_time, e.end_time, CAST('PUBLISHED' AS varchar(30)),
                               CAST(NULL AS nvarchar(1000)), e.minimum_price
                        FROM dbo.vw_PublicEvents e JOIN dbo.tc_event_categories c ON c.id = e.category_id
                        WHERE e.event_id = :eventId
                        """)
                .setParameter("eventId", eventId).getResultList();
        return rows.isEmpty() ? null : eventView(rows.getFirst());
    }

    public List<ZoneView> findPublicZones(EntityManager entityManager, UUID eventId) {
        @SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT availability.zone_id, availability.name, availability.type, availability.price,
                               availability.capacity, availability.held_quantity, availability.sold_quantity,
                               availability.available_quantity
                        FROM dbo.vw_ZoneInventory inventory
                        CROSS APPLY dbo.fn_GetZoneAvailability(inventory.zone_id) availability
                        WHERE availability.event_id = :eventId AND EXISTS (
                            SELECT 1 FROM dbo.vw_PublicEvents WHERE event_id = :eventId)
                        ORDER BY name, zone_id
                        """)
                .setParameter("eventId", eventId).getResultList();
        return rows.stream().map(row -> {
            UUID zoneId = uuid(row[0]);
            return new ZoneView(zoneId, (String) row[1], (String) row[2], (BigDecimal) row[3],
                    ((Number) row[4]).intValue(), ((Number) row[5]).intValue(),
                    ((Number) row[6]).intValue(), ((Number) row[7]).intValue(), findSeats(entityManager, zoneId));
        }).toList();
    }

    public List<Object[]> findCategories(EntityManager entityManager) {
        @SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery(
                "SELECT id, name, slug FROM dbo.tc_event_categories ORDER BY name, id").getResultList();
        return rows;
    }

    private UUID requireEditableEvent(EntityManager entityManager, UUID actorId, UUID eventId) {
        @SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT organization_id, status FROM dbo.tc_events WITH (UPDLOCK, HOLDLOCK) WHERE id = :eventId
                        """).setParameter("eventId", eventId).getResultList();
        if (rows.isEmpty()) throw new NoSuchElementException("event not found");
        UUID organizationId = uuid(rows.getFirst()[0]);
        requireManager(entityManager, actorId, organizationId);
        String status = (String) rows.getFirst()[1];
        if (!"DRAFT".equals(status) && !"REJECTED".equals(status)) {
            throw new IllegalStateException("event can no longer be edited");
        }
        return organizationId;
    }

    private UUID editableZoneEvent(EntityManager entityManager, UUID actorId, UUID zoneId) {
        @SuppressWarnings("unchecked") List<UUID> rows = entityManager.createNativeQuery(
                        "SELECT event_id FROM dbo.tc_zones WITH (UPDLOCK, HOLDLOCK) WHERE id = :zoneId", UUID.class)
                .setParameter("zoneId", zoneId).getResultList();
        if (rows.isEmpty()) throw new NoSuchElementException("zone not found");
        requireEditableEvent(entityManager, actorId, rows.getFirst());
        return rows.getFirst();
    }

    private void requireManager(EntityManager entityManager, UUID actorId, UUID organizationId) {
        Number count = (Number) entityManager.createNativeQuery("""
                        SELECT COUNT_BIG(*) FROM dbo.tc_organization_memberships
                        WHERE user_id = :actorId AND organization_id = :organizationId
                          AND role = 'MANAGER' AND active = 1
                        """)
                .setParameter("actorId", actorId).setParameter("organizationId", organizationId).getSingleResult();
        if (count.longValue() == 0) throw new SecurityException("active manager membership required");
    }

    private void setActor(EntityManager entityManager, UUID actorId) {
        entityManager.createNativeQuery("EXEC sys.sp_set_session_context @key=N'actor_id', @value=:actorId")
                .setParameter("actorId", actorId.toString()).executeUpdate();
    }

    private void bindSearch(Query query, EventSearch search) {
        if (search.query() != null) query.setParameter("query", "%" + search.query() + "%");
        if (search.categoryId() != null) query.setParameter("categoryId", search.categoryId());
        if (search.from() != null) query.setParameter("from", search.from());
        if (search.to() != null) query.setParameter("to", search.to());
    }

    private List<SeatSummaryDto> findSeats(EntityManager entityManager, UUID zoneId) {
        @SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT id, row_name, seat_number, status FROM dbo.tc_seats
                        WHERE zone_id = :zoneId ORDER BY row_name, seat_number, id
                        """).setParameter("zoneId", zoneId).getResultList();
        return rows.stream().map(row -> new SeatSummaryDto(uuid(row[0]), (String) row[1],
                row[2].toString(), EventEnums.SeatStatus.valueOf((String) row[3]))).toList();
    }

    private EventView eventView(Object[] row) {
        return new EventView(uuid(row[0]), uuid(row[1]), uuid(row[2]), (String) row[3],
                (String) row[4], (String) row[5], (String) row[6], (String) row[7], (String) row[8],
                instant(row[9]), instant(row[10]), instant(row[11]), instant(row[12]),
                (String) row[13], (String) row[14], (BigDecimal) row[15]);
    }

    private static UUID uuid(Object value) {
        if (value == null) return null;
        if (value instanceof UUID u) return u;
        return UUID.fromString(value.toString());
    }

    private Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        return ((Timestamp) value).toInstant();
    }
}
