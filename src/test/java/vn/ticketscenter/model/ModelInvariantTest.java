package vn.ticketscenter.model;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.model.event.Event;
import vn.ticketscenter.model.event.EventCategory;
import vn.ticketscenter.model.event.Zone;
import vn.ticketscenter.model.identity.Organization;

import java.math.BigDecimal;
import java.time.Instant;

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
                new Zone(event, "A", ModelEnums.ZoneType.SEATED, new BigDecimal("-1"), null));
        assertThrows(IllegalArgumentException.class, () ->
                new Zone(event, "Đứng", ModelEnums.ZoneType.STANDING, BigDecimal.ZERO, null));
    }

    @Test
    void allowsSalesToEndWhenEventStarts() {
        Instant now = Instant.parse("2026-09-27T00:00:00Z");
        assertDoesNotThrow(() -> new Event(organization, category, "Đêm nhạc", "Nhà hát", "Hà Nội",
                now, now.plusSeconds(10), now.plusSeconds(10), now.plusSeconds(30)));
    }

    private Event validEvent() {
        Instant now = Instant.parse("2026-09-27T00:00:00Z");
        return new Event(organization, category, "Đêm nhạc", "Nhà hát", "Hà Nội",
                now, now.plusSeconds(10), now.plusSeconds(20), now.plusSeconds(30));
    }
}
