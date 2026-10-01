package vn.ticketscenter.event.dto;

import java.util.List;
import java.util.UUID;

public final class EventCancellationDtos {
    private EventCancellationDtos() {}

    public record CancellationException(
            String type, UUID exceptionId, UUID orderId, String status
    ) {}

    public record CancellationProgress(
            UUID eventId,
            String status,
            long totalOrders,
            long pendingOrders,
            long completedOrders,
            long exceptionCount,
            long requestsCreated,
            long refundsSucceeded,
            long refundsPending,
            List<CancellationException> exceptions
    ) {
        public CancellationProgress {
            exceptions = List.copyOf(exceptions);
        }
    }
}
