package vn.ticketscenter.fulfillment;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.fulfillment.service.TicketIdentity;

import static org.junit.jupiter.api.Assertions.*;

class TicketIdentityTest {
    @Test
    void createsUnpredictableCodesAndOneWayHashes() {
        String first = TicketIdentity.newCode();
        String second = TicketIdentity.newCode();
        assertNotEquals(first, second);
        assertEquals(64, TicketIdentity.sha256Hex(first).length());
        assertNotEquals(first, TicketIdentity.sha256Hex(first));
    }
}
