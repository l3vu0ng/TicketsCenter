package vn.ticketscenter.ticketing.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureQuery;
import vn.ticketscenter.ticketing.dto.TicketingDtos.HoldItemRequest;
import vn.ticketscenter.ticketing.dto.TicketingDtos.HoldResponseDto;
import vn.ticketscenter.ticketing.model.TicketingEnums;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class HoldRepository {
    public HoldResponseDto create(EntityManager entityManager, UUID actorId, UUID eventId, List<HoldItemRequest> items) {
        StoredProcedureQuery query = entityManager.createStoredProcedureQuery("dbo.usp_CreateTicketHold")
                .registerStoredProcedureParameter("user_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("event_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("selections", String.class, ParameterMode.IN)
                .setParameter("user_id", actorId).setParameter("event_id", eventId)
                .setParameter("selections", selectionsJson(items));
        query.execute();
        Object[] row = (Object[]) query.getResultList().getFirst();
        UUID holdId = vn.ticketscenter.config.util.InputParser.asUuid(row[0]);
        long total = ((Number) entityManager.createNativeQuery(
                "SELECT COALESCE(SUM(unit_price * quantity), 0) FROM dbo.tc_ticket_hold_items WHERE hold_id = :holdId")
                .setParameter("holdId", holdId).getSingleResult()).longValue();
        return new HoldResponseDto(holdId, eventId, TicketingEnums.TicketHoldStatus.ACTIVE,
                toInstant(row[1]), total);
    }

    public HoldResponseDto release(EntityManager entityManager, UUID holdId, UUID actorId) {
        StoredProcedureQuery query = entityManager.createStoredProcedureQuery("dbo.usp_ReleaseTicketHold")
                .registerStoredProcedureParameter("hold_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("actor_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("reason", String.class, ParameterMode.IN)
                .setParameter("hold_id", holdId).setParameter("actor_id", actorId).setParameter("reason", "USER_CANCEL");
        query.execute();
        Object[] row = (Object[]) query.getResultList().getFirst();
        return new HoldResponseDto(vn.ticketscenter.config.util.InputParser.asUuid(row[0]), null, TicketingEnums.TicketHoldStatus.valueOf(row[1].toString()), null, 0);
    }

    private static String selectionsJson(List<HoldItemRequest> items) {
        return items.stream().map(item -> "{\"zoneId\":\"" + item.zoneId() + "\",\"seatId\":"
                + (item.seatId() == null ? "null" : "\"" + item.seatId() + "\"")
                + ",\"quantity\":" + item.quantity() + "}").reduce((a, b) -> "[" + a + "," + b + "]").orElse("[]");
    }

    private static Instant toInstant(Object value) {
        return value instanceof Timestamp timestamp ? timestamp.toInstant() : ((java.time.LocalDateTime) value).toInstant(java.time.ZoneOffset.UTC);
    }
}
