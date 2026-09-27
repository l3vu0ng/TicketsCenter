package vn.ticketscenter.event.model;

public final class EventEnums {
    private EventEnums() {}

    public enum EventStatus { DRAFT, PENDING_APPROVAL, REJECTED, PUBLISHED, CANCELLED }
    public enum ZoneType { SEATED, STANDING }
    public enum SeatStatus { AVAILABLE, HELD, SOLD }
}
