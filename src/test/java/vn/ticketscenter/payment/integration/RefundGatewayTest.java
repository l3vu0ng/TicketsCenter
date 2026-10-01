package vn.ticketscenter.payment.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefundGatewayTest {
    @TempDir Path stateDirectory;

    @Test
    void restartRecoversSuccessAfterTimeoutWithoutSubmittingTwice() throws Exception {
        UUID refundId = UUID.fromString("40000000-0000-0000-0000-000000000001");
        SimulatedRefundGateway first = new SimulatedRefundGateway(
                stateDirectory, RefundGateway.Status.SUCCEEDED, true);

        assertThrows(RefundGateway.Timeout.class,
                () -> first.submit(refundId, new BigDecimal("400000")));

        SimulatedRefundGateway restarted = new SimulatedRefundGateway(
                stateDirectory, RefundGateway.Status.FAILED, false);
        RefundGateway.Result replay = restarted.submit(refundId, new BigDecimal("400000"));

        assertEquals("SIM-" + refundId, replay.reference());
        assertEquals(RefundGateway.Status.SUCCEEDED, replay.status());
        assertEquals(replay, restarted.query(replay.reference()));
        try (var files = Files.list(stateDirectory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void supportsEachConfiguredProviderOutcome() {
        for (RefundGateway.Status status : RefundGateway.Status.values()) {
            UUID refundId = UUID.randomUUID();
            SimulatedRefundGateway gateway = new SimulatedRefundGateway(
                    stateDirectory.resolve(status.name()), status, false);

            assertEquals(status, gateway.submit(refundId, BigDecimal.ONE).status());
            assertEquals(status, gateway.query("SIM-" + refundId).status());
        }
    }
}
