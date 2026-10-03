package vn.ticketscenter.audit.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

public final class AuditRepository {
    public AuditPage find(EntityManager entityManager, AuditFilter filter) {
        String where = " WHERE (:action IS NULL OR a.action = :action)"
                + " AND (:aggregateType IS NULL OR a.aggregate_type = :aggregateType)"
                + " AND a.created_at >= :fromUtc AND a.created_at < :toUtc";
        Number total = (Number) bind(entityManager.createNativeQuery(
                "SELECT COUNT_BIG(*) FROM dbo.tc_audit_logs a" + where), filter).getSingleResult();
        @SuppressWarnings("unchecked") List<Object[]> rows = bind(entityManager.createNativeQuery("""
                SELECT a.id, a.actor_id, u.email, a.action, a.aggregate_type, a.aggregate_id,
                       CASE WHEN a.action IN ('EVENT_STATUS_CHANGED','REFUND_REQUEST_REJECTED','CANCELLED_EVENT_USED_TICKET')
                            THEN a.detail ELSE NULL END detail,
                       a.created_at
                FROM dbo.tc_audit_logs a
                LEFT JOIN dbo.tc_users u ON u.id = a.actor_id
                """ + where + " ORDER BY a.created_at DESC, a.id DESC"
                + " OFFSET :offset ROWS FETCH NEXT :pageSize ROWS ONLY"), filter)
                .setParameter("offset", (filter.page() - 1) * filter.pageSize())
                .setParameter("pageSize", filter.pageSize()).getResultList();
        return new AuditPage(rows.stream().map(AuditRepository::entry).toList(), filter.page(), filter.pageSize(), total.longValue());
    }

    public AuditEntry findOne(EntityManager entityManager, long id) {
        @SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT a.id, a.actor_id, u.email, a.action, a.aggregate_type, a.aggregate_id,
                       CASE WHEN a.action IN ('EVENT_STATUS_CHANGED','REFUND_REQUEST_REJECTED','CANCELLED_EVENT_USED_TICKET')
                            THEN a.detail ELSE NULL END detail,
                       a.created_at
                FROM dbo.tc_audit_logs a
                LEFT JOIN dbo.tc_users u ON u.id = a.actor_id
                WHERE a.id = :id
                """).setParameter("id", id).getResultList();
        if (rows.isEmpty()) throw new NoSuchElementException("audit entry not found");
        return entry(rows.getFirst());
    }

    private static Query bind(Query query, AuditFilter filter) {
        return query.setParameter("action", filter.action()).setParameter("aggregateType", filter.aggregateType())
                .setParameter("fromUtc", filter.from()).setParameter("toUtc", filter.to());
    }

    private static AuditEntry entry(Object[] row) {
        return new AuditEntry(((Number) row[0]).longValue(), vn.ticketscenter.config.util.InputParser.asUuid(row[1]), (String) row[2], (String) row[3],
                (String) row[4], vn.ticketscenter.config.util.InputParser.asUuid(row[5]), (String) row[6], instant(row[7]));
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof LocalDateTime dateTime) return dateTime.toInstant(ZoneOffset.UTC);
        throw new IllegalStateException("unsupported audit timestamp");
    }

    public record AuditFilter(String action, String aggregateType, Instant from, Instant to, int page, int pageSize) {
        public AuditFilter validate() {
            if (from == null || to == null || !from.isBefore(to)) throw new IllegalArgumentException("invalid audit range");
            if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("invalid pagination");
            return new AuditFilter(token(action, "action"), token(aggregateType, "aggregateType"), from, to, page, pageSize);
        }

        private static String token(String value, String field) {
            if (value == null || value.isBlank()) return null;
            String token = value.trim().toUpperCase(java.util.Locale.ROOT);
            if (token.length() > 100 || !token.matches("[A-Z0-9_]+")) throw new IllegalArgumentException("invalid " + field);
            return token;
        }
    }

    public record AuditEntry(long id, UUID actorId, String actorEmail, String action,
                             String aggregateType, UUID aggregateId, String detail, Instant createdAt) {}
    public record AuditPage(List<AuditEntry> items, int page, int pageSize, long total) {}
}
