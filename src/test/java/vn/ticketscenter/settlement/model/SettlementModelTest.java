package vn.ticketscenter.settlement.model;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.event.model.Event;
import vn.ticketscenter.event.model.EventCategory;
import vn.ticketscenter.identity.model.Organization;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettlementModelTest {
    @Test
    void validatesItemAmountsAndSettlementTransitions() {
        SettlementItem.validateAmounts(new BigDecimal("300000"), new BigDecimal("100000"),
                new BigDecimal("21000"), new BigDecimal("179000"));
        assertThrows(IllegalArgumentException.class, () -> SettlementItem.validateAmounts(
                new BigDecimal("300000"), new BigDecimal("100000"),
                new BigDecimal("21000"), new BigDecimal("1")));

        Settlement settlement = new Settlement(event(), Instant.EPOCH);
        settlement.recalculate(new BigDecimal("300000"), new BigDecimal("100000"), new BigDecimal("21000"));
        settlement.confirm(Instant.parse("2026-10-01T00:00:00Z"));
        assertEquals(SettlementEnums.SettlementStatus.CONFIRMED, settlement.getStatus());
        assertThrows(IllegalStateException.class, () -> settlement.recalculate(
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO));
        settlement.markPaid();
        assertEquals(SettlementEnums.SettlementStatus.PAID, settlement.getStatus());
    }

    @Test
    void payoutOutcomeCannotRegress() {
        Settlement settlement = new Settlement(event(), Instant.EPOCH);
        settlement.recalculate(BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO);
        settlement.confirm(Instant.EPOCH.plusSeconds(1));
        Payout payout = new Payout(settlement, BigDecimal.TEN, "SIM-1", Instant.EPOCH);

        payout.markSucceeded(Instant.EPOCH.plusSeconds(2));

        assertEquals(SettlementEnums.PayoutStatus.SUCCEEDED, payout.getStatus());
        assertThrows(IllegalStateException.class, payout::markFailed);
    }

    private static Event event() {
        Organization organization = new Organization("Org", "org@example.test", Instant.EPOCH);
        EventCategory category = new EventCategory("Music", "music");
        return new Event(organization, category, "Event", "Venue", "Address",
                Instant.EPOCH, Instant.EPOCH.plusSeconds(1), Instant.EPOCH.plusSeconds(2),
                Instant.EPOCH.plusSeconds(3));
    }
}
