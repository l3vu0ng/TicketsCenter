package vn.ticketscenter.event.dto;

import vn.ticketscenter.event.model.EventEnums;

import java.time.Instant;
import java.util.UUID;

public final class EventDtos {
    private EventDtos() {}

    public record EventSummaryDto(
            UUID id,
            String title,
            String location,
            Instant startTime,
            Instant endTime,
            EventEnums.EventStatus status,
            UUID organizationId
    ) {}

    public record ZoneSummaryDto(
            UUID id,
            String name,
            EventEnums.ZoneType type,
            long price,
            int capacity
    ) {}

    public record SeatSummaryDto(
            UUID id,
            String rowCode,
            String seatNumber,
            EventEnums.SeatStatus status
    ) {}
}
