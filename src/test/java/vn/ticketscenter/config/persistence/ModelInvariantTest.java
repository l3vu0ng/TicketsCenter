package vn.ticketscenter.config.persistence;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.event.model.Event;
import vn.ticketscenter.event.model.EventCategory;
import vn.ticketscenter.event.model.EventEnums;
import vn.ticketscenter.event.model.Zone;
import vn.ticketscenter.fulfillment.model.FulfillmentEnums;
import vn.ticketscenter.identity.model.IdentityEnums;
import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.order.model.OrderEnums;
import vn.ticketscenter.settlement.model.SettlementEnums;
import vn.ticketscenter.ticketing.model.TicketingEnums;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ModelInvariantTest {

    private final Organization organization = new Organization("Nhà tổ chức", "org@example.com", Instant.EPOCH);
    private final EventCategory category = new EventCategory("Âm nhạc", "am-nhac");

    @Test
    void rejectsInvalidEventSchedule() {
        Instant now = Instant.parse("2026-09-27T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new Event(
                organization, category, "Đêm nhạc", "Nhà hát", "Hà Nội",
                now.plusSeconds(20), now.plusSeconds(10), now.plusSeconds(30), now.plusSeconds(40)));
    }

    @Test
    void rejectsNegativeZonePriceAndMissingStandingCapacity() {
        Event event = validEvent();
        assertThrows(IllegalArgumentException.class, () ->
                new Zone(event, "A", EventEnums.ZoneType.SEATED, new BigDecimal("-1"), null));
        assertThrows(IllegalArgumentException.class, () ->
                new Zone(event, "Đứng", EventEnums.ZoneType.STANDING, BigDecimal.ZERO, null));
    }

    @Test
    void allowsSalesToEndWhenEventStarts() {
        Instant now = Instant.parse("2026-09-27T00:00:00Z");
        assertDoesNotThrow(() -> new Event(organization, category, "Đêm nhạc", "Nhà hát", "Hà Nội",
                now, now.plusSeconds(10), now.plusSeconds(10), now.plusSeconds(30)));
    }

    @Test
    void featureEnumsPreserveDatabaseValues() {
        assertArrayEquals(new String[]{"ACTIVE", "DISABLED"}, names(IdentityEnums.UserStatus.values()));
        assertArrayEquals(new String[]{"MANAGER", "CHECK_IN_STAFF"}, names(IdentityEnums.OrganizationRole.values()));
        assertArrayEquals(new String[]{"PENDING", "APPROVED", "REJECTED"}, names(IdentityEnums.OrganizationRequestStatus.values()));
        assertArrayEquals(new String[]{"VERIFY_EMAIL", "RESET_PASSWORD"}, names(IdentityEnums.OtpPurpose.values()));
        assertArrayEquals(new String[]{"DRAFT", "PENDING_APPROVAL", "REJECTED", "PUBLISHED", "CANCELLED"}, names(EventEnums.EventStatus.values()));
        assertArrayEquals(new String[]{"SEATED", "STANDING"}, names(EventEnums.ZoneType.values()));
        assertArrayEquals(new String[]{"AVAILABLE", "HELD", "SOLD"}, names(EventEnums.SeatStatus.values()));
        assertArrayEquals(new String[]{"ACTIVE", "RELEASED", "CONSUMED"}, names(TicketingEnums.TicketHoldStatus.values()));
        assertArrayEquals(new String[]{"PENDING_PAYMENT", "PAID", "CANCELLED", "EXPIRED"}, names(OrderEnums.OrderStatus.values()));
        assertArrayEquals(new String[]{"PENDING", "CAPTURED", "FAILED", "UNKNOWN"}, names(OrderEnums.PaymentStatus.values()));
        assertArrayEquals(new String[]{"PERCENTAGE", "FIXED_AMOUNT"}, names(OrderEnums.DiscountType.values()));
        assertArrayEquals(new String[]{"ACTIVE", "USED", "REFUND_PENDING", "REFUNDED", "INVALIDATED"}, names(FulfillmentEnums.TicketStatus.values()));
        assertArrayEquals(new String[]{"SUCCESS", "NOT_FOUND", "WRONG_EVENT", "TOO_EARLY", "TOO_LATE", "ALREADY_USED", "NOT_ACTIVE", "EVENT_CANCELLED"}, names(FulfillmentEnums.CheckInResult.values()));
        assertArrayEquals(new String[]{"PENDING", "APPROVED", "REJECTED", "COMPLETED"}, names(FulfillmentEnums.RefundRequestStatus.values()));
        assertArrayEquals(new String[]{"CUSTOMER_REQUEST", "EVENT_CANCELLATION"}, names(FulfillmentEnums.RefundRequestReason.values()));
        assertArrayEquals(new String[]{"CUSTOMER_REFUND", "PAYMENT_COMPENSATION"}, names(FulfillmentEnums.RefundPurpose.values()));
        assertArrayEquals(new String[]{"PENDING", "SUCCEEDED", "FAILED", "UNKNOWN"}, names(FulfillmentEnums.RefundStatus.values()));
        assertArrayEquals(new String[]{"DRAFT", "CONFIRMED", "PAID"}, names(SettlementEnums.SettlementStatus.values()));
        assertArrayEquals(new String[]{"PENDING", "SUCCEEDED", "FAILED"}, names(SettlementEnums.PayoutStatus.values()));
    }

    private String[] names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toArray(String[]::new);
    }

    private Event validEvent() {
        Instant now = Instant.parse("2026-09-27T00:00:00Z");
        return new Event(organization, category, "Đêm nhạc", "Nhà hát", "Hà Nội",
                now, now.plusSeconds(10), now.plusSeconds(20), now.plusSeconds(30));
    }
}
