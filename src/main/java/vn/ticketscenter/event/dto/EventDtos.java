package vn.ticketscenter.event.dto;

import vn.ticketscenter.event.model.EventEnums;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
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

    public record CreateEventCommand(
            UUID categoryId, String title, String description, String venueName, String venueAddress,
            Instant saleStart, Instant saleEnd, Instant startTime, Instant endTime
    ) {}

    public record ZoneCommand(
            String name, String type, BigDecimal price, Integer capacity, Integer rows, Integer seatsPerRow
    ) {}

    public record SeatSpec(String rowName, int seatNumber, String label) {}

    public record EventView(
            UUID id, UUID organizationId, UUID categoryId, String categoryName, String title,
            String description, String coverImageUrl, String venueName, String venueAddress,
            Instant saleStart, Instant saleEnd, Instant startTime, Instant endTime,
            String status, String rejectionReason, BigDecimal minimumPrice
    ) {}

    public record ZoneView(
            UUID id, String name, String type, BigDecimal price,
            int capacity, int heldQuantity, int soldQuantity, int availableQuantity,
            List<SeatSummaryDto> seats
    ) {}

    public record EventPage(List<EventView> items, int page, int pageSize, long total) {}

    public record EventSearch(
            String query, UUID categoryId, Instant from, Instant to, String sort, int page, int pageSize
    ) {}
}
