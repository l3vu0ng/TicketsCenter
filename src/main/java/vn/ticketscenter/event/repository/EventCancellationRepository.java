package vn.ticketscenter.event.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import vn.ticketscenter.event.dto.EventCancellationDtos.CancellationException;
import vn.ticketscenter.event.dto.EventCancellationDtos.CancellationProgress;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

public class EventCancellationRepository {

    public record CancellationWork(UUID eventId, String type, UUID id) {}

    public void cancel(EntityManager entityManager, UUID eventId, UUID actorId) {
        entityManager.createStoredProcedureQuery("dbo.usp_CancelEvent")
                .registerStoredProcedureParameter("event_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("actor_id", UUID.class, ParameterMode.IN)
                .setParameter("event_id", eventId)
                .setParameter("actor_id", actorId)
                .execute();
        entityManager.clear();
    }

    public CancellationProgress progress(EntityManager entityManager, UUID eventId) {
        @SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT event_id, event_status, total_orders, pending_orders, completed_orders,
                               exception_count, requests_created, refunds_succeeded, refunds_pending
                        FROM dbo.vw_EventCancellationProgress WHERE event_id = :eventId
                        """).setParameter("eventId", eventId).getResultList();
        if (rows.isEmpty()) throw new NoSuchElementException("event not found");
        Object[] row = rows.getFirst();
        @SuppressWarnings("unchecked") List<Object[]> exceptionRows = entityManager.createNativeQuery("""
                        SELECT exception_type, exception_id, order_id, status
                        FROM dbo.vw_EventCancellationExceptions
                        WHERE event_id = :eventId ORDER BY exception_type, exception_id
                        """).setParameter("eventId", eventId).getResultList();
        List<CancellationException> exceptions = exceptionRows.stream()
                .map(value -> new CancellationException(
                        (String) value[0], (UUID) value[1], (UUID) value[2], (String) value[3]))
                .toList();
        return new CancellationProgress((UUID) row[0], (String) row[1], number(row[2]), number(row[3]),
                number(row[4]), number(row[5]), number(row[6]), number(row[7]), number(row[8]), exceptions);
    }

    public List<CancellationWork> findWork(EntityManager entityManager, int limit) {
        @SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT event_id, work_type, work_id
                        FROM dbo.vw_EventCancellationWork
                        ORDER BY event_id, work_type, work_id
                        OFFSET 0 ROWS FETCH NEXT :limit ROWS ONLY
                        """).setParameter("limit", limit).getResultList();
        return rows.stream().map(row -> new CancellationWork(
                (UUID) row[0], (String) row[1], (UUID) row[2])).toList();
    }

    public void process(EntityManager entityManager, CancellationWork work) {
        if ("HOLD".equals(work.type())) {
            entityManager.createStoredProcedureQuery("dbo.usp_ReleaseTicketHold")
                    .registerStoredProcedureParameter("hold_id", UUID.class, ParameterMode.IN)
                    .registerStoredProcedureParameter("actor_id", UUID.class, ParameterMode.IN)
                    .registerStoredProcedureParameter("reason", String.class, ParameterMode.IN)
                    .setParameter("hold_id", work.id())
                    .setParameter("actor_id", null)
                    .setParameter("reason", "EVENT_CANCELLED")
                    .execute();
        } else if ("ORDER".equals(work.type())) {
            entityManager.createStoredProcedureQuery("dbo.usp_ProcessCancelledOrder")
                    .registerStoredProcedureParameter("event_id", UUID.class, ParameterMode.IN)
                    .registerStoredProcedureParameter("order_id", UUID.class, ParameterMode.IN)
                    .setParameter("event_id", work.eventId())
                    .setParameter("order_id", work.id())
                    .execute();
        } else {
            throw new IllegalArgumentException("unsupported cancellation work type");
        }
        entityManager.clear();
    }

    public void completeFinishedEvents(EntityManager entityManager) {
        entityManager.createNativeQuery("""
                        UPDATE x SET status = 'PUBLISHED', published_at = SYSUTCDATETIME(),
                                     lease_owner = NULL, lease_until = NULL
                        FROM dbo.tc_outbox x
                        WHERE x.event_type = 'EVENT_CANCELLED' AND x.status <> 'PUBLISHED'
                          AND NOT EXISTS (
                              SELECT 1 FROM dbo.vw_EventCancellationWork w
                              WHERE w.event_id = x.aggregate_id)
                        """).executeUpdate();
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }
}
