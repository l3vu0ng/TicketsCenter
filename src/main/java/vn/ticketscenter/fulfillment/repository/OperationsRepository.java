package vn.ticketscenter.fulfillment.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import vn.ticketscenter.fulfillment.dto.FulfillmentDtos.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OperationsRepository {
    public CheckInResultView checkIn(EntityManager em, UUID eventId, UUID actorId, String code) {
        var q=em.createStoredProcedureQuery("dbo.usp_CheckInTicket").registerStoredProcedureParameter("event_id",UUID.class,ParameterMode.IN).registerStoredProcedureParameter("actor_id",UUID.class,ParameterMode.IN).registerStoredProcedureParameter("ticket_code",String.class,ParameterMode.IN).setParameter("event_id",eventId).setParameter("actor_id",actorId).setParameter("ticket_code",code);
        q.execute(); Object[] r=(Object[])q.getResultList().getFirst(); return new CheckInResultView((String)r[0],(UUID)r[2],((Timestamp)r[1]).toInstant());
    }
    public List<RefundableTicket> refundable(EntityManager em, UUID orderId) {
        return em.createNativeQuery("SELECT ticket_id,paid_amount,zone_name_snapshot,seat_label_snapshot FROM dbo.fn_GetRefundableTickets(:id,SYSUTCDATETIME())").setParameter("id",orderId).getResultList().stream().map(x->{Object[]r=(Object[])x;return new RefundableTicket((UUID)r[0],(BigDecimal)r[1],(String)r[2],(String)r[3]);}).toList();
    }
    public RefundRequestResult requestRefund(EntityManager em, UUID orderId, UUID actorId, String ticketIds, String reason) {
        var q=em.createStoredProcedureQuery("dbo.usp_CreateRefundRequest").registerStoredProcedureParameter("order_id",UUID.class,ParameterMode.IN).registerStoredProcedureParameter("actor_id",UUID.class,ParameterMode.IN).registerStoredProcedureParameter("ticket_ids",String.class,ParameterMode.IN).registerStoredProcedureParameter("reason",String.class,ParameterMode.IN).setParameter("order_id",orderId).setParameter("actor_id",actorId).setParameter("ticket_ids",ticketIds).setParameter("reason",reason); q.execute(); Object[]r=(Object[])em.createNativeQuery("SELECT TOP 1 rr.id, SUM(t.paid_amount) FROM dbo.tc_refund_requests rr JOIN dbo.tc_refund_request_tickets rt ON rt.refund_request_id=rr.id JOIN dbo.tc_tickets t ON t.id=rt.ticket_id WHERE rr.order_id=:o AND rr.requester_id=:a ORDER BY rr.requested_at DESC").setParameter("o",orderId).setParameter("a",actorId).getResultList().getFirst(); return new RefundRequestResult((UUID)r[0],(BigDecimal)r[1]);
    }
    public String decideRefund(EntityManager em, UUID requestId, UUID actorId, String decision, String reason) {
        var q=em.createStoredProcedureQuery("dbo.usp_DecideRefundRequest").registerStoredProcedureParameter("request_id",UUID.class,ParameterMode.IN).registerStoredProcedureParameter("actor_id",UUID.class,ParameterMode.IN).registerStoredProcedureParameter("decision",String.class,ParameterMode.IN).registerStoredProcedureParameter("rejection_reason",String.class,ParameterMode.IN).registerStoredProcedureParameter("retry_failed",Boolean.class,ParameterMode.IN).setParameter("request_id",requestId).setParameter("actor_id",actorId).setParameter("decision",decision).setParameter("rejection_reason",reason).setParameter("retry_failed",false);q.execute(); return decision;
    }
}
