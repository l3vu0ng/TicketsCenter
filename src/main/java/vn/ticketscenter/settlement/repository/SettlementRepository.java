package vn.ticketscenter.settlement.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureQuery;
import vn.ticketscenter.settlement.dto.SettlementDtos.PayoutBalance;
import vn.ticketscenter.settlement.dto.SettlementDtos.SettlementBlocker;
import vn.ticketscenter.settlement.dto.SettlementDtos.SettlementSnapshot;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public class SettlementRepository {
    public SettlementSnapshot recalculate(EntityManager entityManager, UUID eventId, UUID actorId) {
        StoredProcedureQuery query = entityManager.createStoredProcedureQuery("dbo.usp_RecalculateSettlement")
                .registerStoredProcedureParameter("event_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("actor_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("return_result", Boolean.class, ParameterMode.IN)
                .setParameter("event_id", eventId)
                .setParameter("actor_id", actorId)
                .setParameter("return_result", true);
        query.execute();
        Object[] row = first(query);
        entityManager.clear();
        return new SettlementSnapshot((UUID) row[0], (String) row[1], money(row[2]), money(row[3]),
                money(row[4]), money(row[5]));
    }

    public PayoutBalance confirm(EntityManager entityManager, UUID settlementId, UUID actorId) {
        StoredProcedureQuery query = entityManager.createStoredProcedureQuery("dbo.usp_ConfirmSettlement")
                .registerStoredProcedureParameter("settlement_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("actor_id", UUID.class, ParameterMode.IN)
                .setParameter("settlement_id", settlementId)
                .setParameter("actor_id", actorId);
        query.execute();
        PayoutBalance balance = balance(first(query));
        entityManager.clear();
        return balance;
    }

    public PayoutBalance recordPayout(EntityManager entityManager, UUID settlementId, UUID payoutId,
                                      UUID actorId, BigDecimal amount, String reference, String result) {
        StoredProcedureQuery query = entityManager.createStoredProcedureQuery("dbo.usp_RecordPayout")
                .registerStoredProcedureParameter("settlement_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("payout_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("actor_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("amount", BigDecimal.class, ParameterMode.IN)
                .registerStoredProcedureParameter("reference", String.class, ParameterMode.IN)
                .registerStoredProcedureParameter("verified_result", String.class, ParameterMode.IN)
                .setParameter("settlement_id", settlementId)
                .setParameter("payout_id", payoutId)
                .setParameter("actor_id", actorId)
                .setParameter("amount", amount)
                .setParameter("reference", reference)
                .setParameter("verified_result", result);
        query.execute();
        PayoutBalance balance = balance(first(query));
        entityManager.clear();
        return balance;
    }

    public List<SettlementBlocker> blockers(EntityManager entityManager, UUID eventId) {
        @SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT blocker_type, blocker_id, related_id
                        FROM dbo.fn_GetSettlementBlockers(:eventId, SYSUTCDATETIME())
                        ORDER BY blocker_type, blocker_id
                        """).setParameter("eventId", eventId).getResultList();
        return rows.stream().map(row -> new SettlementBlocker(
                (String) row[0], (UUID) row[1], (UUID) row[2])).toList();
    }

    public PayoutBalance balance(EntityManager entityManager, UUID settlementId) {
        @SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT settlement_id, event_id, gross_revenue, total_refund, total_commission,
                               net_payable, status, confirmed_at, paid_amount, pending_amount,
                               remaining_amount, available_amount
                        FROM dbo.vw_SettlementPayoutBalance
                        WHERE settlement_id = :settlementId
                        """).setParameter("settlementId", settlementId).getResultList();
        if (rows.isEmpty()) throw new java.util.NoSuchElementException("settlement not found");
        return balance(rows.getFirst());
    }

    private static Object[] first(StoredProcedureQuery query) {
        @SuppressWarnings("unchecked") List<Object[]> rows = query.getResultList();
        if (rows.isEmpty()) throw new IllegalStateException("settlement procedure returned no result");
        return rows.getFirst();
    }

    private static PayoutBalance balance(Object[] row) {
        return new PayoutBalance((UUID) row[0], (UUID) row[1], money(row[2]), money(row[3]),
                money(row[4]), money(row[5]), (String) row[6], instant(row[7]), money(row[8]),
                money(row[9]), money(row[10]), money(row[11]));
    }

    private static BigDecimal money(Object value) {
        return value instanceof BigDecimal amount ? amount : new BigDecimal(value.toString());
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof LocalDateTime dateTime) return dateTime.toInstant(ZoneOffset.UTC);
        throw new IllegalStateException("unsupported settlement timestamp");
    }
}
