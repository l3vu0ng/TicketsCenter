package vn.ticketscenter.order.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureQuery;
import vn.ticketscenter.order.dto.OrderDtos.OrderResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class OrderRepository {
    public OrderResult createFromHold(EntityManager entityManager, UUID holdId, UUID actorId) {
        StoredProcedureQuery query = entityManager.createStoredProcedureQuery("dbo.usp_CreateOrderFromHold")
                .registerStoredProcedureParameter("hold_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("actor_id", UUID.class, ParameterMode.IN)
                .setParameter("hold_id", holdId).setParameter("actor_id", actorId);
        query.execute();
        @SuppressWarnings("unchecked") List<Object[]> rows = query.getResultList();
        if (rows.isEmpty()) throw new IllegalStateException("order creation returned no result");
        Object[] row = rows.getFirst();
        return new OrderResult((UUID) row[0], (String) row[1], (BigDecimal) row[2],
                (BigDecimal) row[3], (BigDecimal) row[4], "PENDING_PAYMENT", null);
    }

    public OrderResult applyCoupon(EntityManager entityManager, UUID orderId, UUID actorId, String couponCode) {
        StoredProcedureQuery query = entityManager.createStoredProcedureQuery("dbo.usp_ApplyOrderCoupon")
                .registerStoredProcedureParameter("order_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("actor_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("coupon_code", String.class, ParameterMode.IN)
                .setParameter("order_id", orderId).setParameter("actor_id", actorId)
                .setParameter("coupon_code", couponCode);
        query.execute();
        @SuppressWarnings("unchecked") List<Object[]> rows = query.getResultList();
        if (rows.isEmpty()) throw new IllegalStateException("coupon update returned no result");
        Object[] row = rows.getFirst();
        return new OrderResult(orderId, null, (BigDecimal) row[0], (BigDecimal) row[1],
                (BigDecimal) row[2], "PENDING_PAYMENT", (UUID) row[3]);
    }
}
