package vn.ticketscenter.settlement.integration;

import java.math.BigDecimal;
import java.util.UUID;

public interface PayoutGateway {
    enum Status { SUCCEEDED, FAILED }

    record Result(UUID payoutId, BigDecimal amount, String reference, Status status) {}

    String reference(UUID payoutId);

    Result submit(UUID payoutId, BigDecimal amount);

    Result query(String reference);
}
