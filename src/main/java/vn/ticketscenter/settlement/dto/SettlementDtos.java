package vn.ticketscenter.settlement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class SettlementDtos {
    private SettlementDtos() {}

    public record SettlementSnapshot(UUID settlementId, String status, BigDecimal grossRevenue,
                                     BigDecimal totalRefund, BigDecimal totalCommission,
                                     BigDecimal netPayable) {}

    public record SettlementBlocker(String type, UUID blockerId, UUID relatedId) {}

    public record PayoutBalance(UUID settlementId, UUID eventId, BigDecimal grossRevenue,
                                BigDecimal totalRefund, BigDecimal totalCommission,
                                BigDecimal netPayable, String status, Instant confirmedAt,
                                BigDecimal paidAmount, BigDecimal pendingAmount,
                                BigDecimal remainingAmount, BigDecimal availableAmount) {}
}
