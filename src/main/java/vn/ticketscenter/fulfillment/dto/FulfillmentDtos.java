package vn.ticketscenter.fulfillment.dto;

import vn.ticketscenter.fulfillment.model.FulfillmentEnums;

import java.time.Instant;
import java.util.UUID;

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
}
