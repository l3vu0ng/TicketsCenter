package vn.ticketscenter.fulfillment.dto;

import vn.ticketscenter.fulfillment.model.FulfillmentEnums;

import java.time.Instant;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.List;

public final class FulfillmentDtos {
    private FulfillmentDtos() {}

    public record CheckInScanRequest(
            UUID eventId,
            String qrToken
    ) {}

    public record CheckInScanResponse(
            FulfillmentEnums.CheckInResult result,
            String message,
            UUID ticketId,
            String attendeeName,
            Instant checkedInAt
    ) {}

    public record RefundApplicationRequest(
            UUID orderId,
            String reason,
            FulfillmentEnums.RefundRequestReason reasonType
    ) {}

    public record TicketView(UUID ticketId, UUID eventId, String eventTitle, String venueName,
                             String venueAddress, Instant startTime, String zoneName,
                             String seatLabel, BigDecimal paidAmount, String status, Instant issuedAt) {}

    public record PaymentConfirmation(String orderStatus, String paymentStatus, List<TicketView> tickets) {}
}
