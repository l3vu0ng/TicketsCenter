package vn.ticketscenter.payment.integration;

import java.math.BigDecimal;
import java.util.UUID;

public interface RefundGateway {
    enum Status { SUCCEEDED, FAILED, UNKNOWN }

    record Result(UUID refundId, BigDecimal amount, String reference, Status status) {}

    Result submit(UUID refundId, BigDecimal amount);

    Result query(String reference);

    final class Timeout extends RuntimeException {
        private final String reference;

        public Timeout(String reference) {
            super("simulated timeout after provider side effect");
            this.reference = reference;
        }

        public String reference() {
            return reference;
        }
    }
}
