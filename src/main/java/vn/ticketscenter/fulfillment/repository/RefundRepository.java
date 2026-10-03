package vn.ticketscenter.fulfillment.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.payment.integration.RefundGateway;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class RefundRepository {
    public record RefundWork(UUID refundId, BigDecimal amount, String status, String providerReference) {}

    public List<RefundWork> findWork(EntityManager entityManager, int limit) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT id, amount, status, provider_reference
                FROM dbo.vw_RefundWork
                ORDER BY created_at, id
                OFFSET 0 ROWS FETCH NEXT :limit ROWS ONLY
                """).setParameter("limit", limit).getResultList();
        return rows.stream().map(row -> new RefundWork(
                vn.ticketscenter.config.util.InputParser.asUuid(row[0]), (BigDecimal) row[1], (String) row[2], (String) row[3])).toList();
    }

    public void apply(EntityManager entityManager, UUID refundId, RefundGateway.Result result) {
        String verifiedResult = "{\"status\":" + HttpResponses.jsonString(result.status().name())
                + ",\"providerReference\":" + HttpResponses.jsonString(result.reference()) + "}";
        entityManager.createStoredProcedureQuery("dbo.usp_ApplyRefundResult")
                .registerStoredProcedureParameter("refund_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("verified_result", String.class, ParameterMode.IN)
                .setParameter("refund_id", refundId)
                .setParameter("verified_result", verifiedResult)
                .execute();
    }

    public void retryCustomer(EntityManager entityManager, UUID requestId, UUID actorId) {
        entityManager.createStoredProcedureQuery("dbo.usp_DecideRefundRequest")
                .registerStoredProcedureParameter("request_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("actor_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("decision", String.class, ParameterMode.IN)
                .registerStoredProcedureParameter("rejection_reason", String.class, ParameterMode.IN)
                .registerStoredProcedureParameter("retry_failed", Boolean.class, ParameterMode.IN)
                .setParameter("request_id", requestId)
                .setParameter("actor_id", actorId)
                .setParameter("decision", "APPROVE")
                .setParameter("rejection_reason", null)
                .setParameter("retry_failed", true)
                .execute();
    }

    public void retryCompensation(EntityManager entityManager, UUID paymentId) {
        List<?> matches = entityManager.createNativeQuery("""
                SELECT order_id FROM dbo.vw_FailedCompensationAttempts WHERE payment_id = :paymentId
                """).setParameter("paymentId", paymentId).getResultList();
        if (matches.isEmpty()) throw new IllegalStateException("failed compensation not found");
        UUID orderId = (UUID) matches.getFirst();
        entityManager.createStoredProcedureQuery("dbo.usp_ApplyPaymentResult")
                .registerStoredProcedureParameter("order_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("actor_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("payment_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("verified_result", String.class, ParameterMode.IN)
                .registerStoredProcedureParameter("ticket_codes", String.class, ParameterMode.IN)
                .registerStoredProcedureParameter("retry_failed_compensation", Boolean.class, ParameterMode.IN)
                .setParameter("order_id", orderId)
                .setParameter("actor_id", null)
                .setParameter("payment_id", paymentId)
                .setParameter("verified_result", "{\"status\":\"CAPTURED\"}")
                .setParameter("ticket_codes", "[]")
                .setParameter("retry_failed_compensation", true)
                .execute();
    }
}
