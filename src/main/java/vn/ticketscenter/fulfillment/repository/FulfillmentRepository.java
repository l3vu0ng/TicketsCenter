package vn.ticketscenter.fulfillment.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureQuery;
import vn.ticketscenter.fulfillment.dto.FulfillmentDtos.TicketView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

public class FulfillmentRepository {
    public String applyPayment(EntityManager em, UUID orderId, UUID actorId, UUID paymentId, String providerReference) {
        int quantity = ((Number) em.createNativeQuery("SELECT COALESCE(SUM(quantity),0) FROM dbo.tc_order_items WHERE order_id=:id")
                .setParameter("id", orderId).getSingleResult()).intValue();
        String codes = IntStream.range(0, quantity).mapToObj(i -> {
            String code = vn.ticketscenter.fulfillment.service.TicketIdentity.newCode();
            return "{\"ticketCode\":\"" + code + "\",\"qrSecretHashHex\":\""
                    + vn.ticketscenter.fulfillment.service.TicketIdentity.sha256Hex(code) + "\"}";
        }).reduce((a, b) -> "[" + a + "," + b + "]").orElse("[]");
        String verified = "{\"status\":\"CAPTURED\",\"providerReference\":\""
                + (providerReference == null ? "" : providerReference.replace("\"", "")) + "\"}";
        StoredProcedureQuery query = em.createStoredProcedureQuery("dbo.usp_ApplyPaymentResult")
                .registerStoredProcedureParameter("order_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("actor_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("payment_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("verified_result", String.class, ParameterMode.IN)
                .registerStoredProcedureParameter("ticket_codes", String.class, ParameterMode.IN)
                .registerStoredProcedureParameter("retry_failed_compensation", Boolean.class, ParameterMode.IN)
                .setParameter("order_id", orderId).setParameter("actor_id", actorId).setParameter("payment_id", paymentId)
                .setParameter("verified_result", verified).setParameter("ticket_codes", codes)
                .setParameter("retry_failed_compensation", false);
        query.execute();
        Object[] row = (Object[]) query.getResultList().getFirst();
        return row[0] + ":" + row[1];
    }

    public List<TicketView> findMine(EntityManager em, UUID actorId) {
        return em.createNativeQuery("SELECT ticket_id,event_id,event_title,venue_name,venue_address,start_time,zone_name_snapshot,seat_label_snapshot,paid_amount,status,issued_at FROM dbo.vw_TicketDetails WHERE owner_id=:owner ORDER BY issued_at DESC")
                .setParameter("owner", actorId).getResultList().stream().map(row -> {
                    Object[] r = (Object[]) row;
                    return new TicketView((UUID) r[0], (UUID) r[1], (String) r[2], (String) r[3], (String) r[4],
                            ((java.sql.Timestamp) r[5]).toInstant(), (String) r[6], (String) r[7], (BigDecimal) r[8], (String) r[9], ((java.sql.Timestamp) r[10]).toInstant());
                }).toList();
    }

    public String ticketCode(EntityManager em, UUID actorId, UUID ticketId) {
        List<?> rows = em.createNativeQuery("SELECT t.ticket_code FROM dbo.tc_tickets t JOIN dbo.tc_order_items oi ON oi.id=t.order_item_id JOIN dbo.tc_orders o ON o.id=oi.order_id WHERE t.id=:id AND o.user_id=:owner")
                .setParameter("id", ticketId).setParameter("owner", actorId).getResultList();
        if (rows.isEmpty()) throw new java.util.NoSuchElementException("ticket not found");
        return rows.getFirst().toString();
    }
}
