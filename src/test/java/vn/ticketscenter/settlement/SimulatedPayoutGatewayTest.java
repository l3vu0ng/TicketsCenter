package vn.ticketscenter.settlement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.ticketscenter.settlement.integration.PayoutGateway;
import vn.ticketscenter.settlement.integration.SimulatedPayoutGateway;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimulatedPayoutGatewayTest {
    @TempDir Path directory;

    @Test
    void replayReturnsOnePersistedPayoutResult() {
        UUID payoutId = UUID.randomUUID();
        SimulatedPayoutGateway gateway = new SimulatedPayoutGateway(
                directory, PayoutGateway.Status.SUCCEEDED, false);

        PayoutGateway.Result first = gateway.submit(payoutId, new BigDecimal("300000"));
        PayoutGateway.Result replay = gateway.submit(payoutId, new BigDecimal("300000"));

        assertEquals(first, replay);
        assertEquals(first, gateway.query(first.reference()));
        assertThrows(IllegalStateException.class,
                () -> gateway.submit(payoutId, new BigDecimal("299999")));
    }

    @Test
    void timeoutAfterSideEffectIsRecoveredBySamePayoutId() {
        UUID payoutId = UUID.randomUUID();
        SimulatedPayoutGateway timingOut = new SimulatedPayoutGateway(
                directory, PayoutGateway.Status.SUCCEEDED, true);

        assertThrows(SimulatedPayoutGateway.Timeout.class,
                () -> timingOut.submit(payoutId, new BigDecimal("300000")));

        SimulatedPayoutGateway retry = new SimulatedPayoutGateway(
                directory, PayoutGateway.Status.SUCCEEDED, false);
        assertEquals(PayoutGateway.Status.SUCCEEDED,
                retry.submit(payoutId, new BigDecimal("300000")).status());
    }
}
