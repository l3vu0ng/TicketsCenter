package vn.ticketscenter.ticketing.dto;

import vn.ticketscenter.ticketing.model.TicketingEnums;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TicketingDtos {
    private TicketingDtos() {}

    public record HoldItemRequest(
            UUID zoneId,
            UUID seatId,
            int quantity
    ) {}

    public record CreateHoldRequest(
            UUID eventId,
            List<HoldItemRequest> items
    ) {}

    public record HoldResponseDto(
            UUID holdId,
            UUID eventId,
            TicketingEnums.TicketHoldStatus status,
            Instant expiresAt,
            long totalHoldAmount
    ) {}
}
