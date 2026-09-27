package vn.ticketscenter.order.dto;

import vn.ticketscenter.order.model.OrderEnums;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class VoucherDtos {
    private VoucherDtos() {}

    public record VoucherDto(
            UUID id,
            String code,
            OrderEnums.DiscountType discountType,
            BigDecimal discountValue,
            BigDecimal minOrderAmount,
            BigDecimal maxDiscountAmount,
            Instant validFrom,
            Instant expiresAt,
            boolean active
    ) {}

    public record VoucherValidationRequest(
            String code,
            BigDecimal orderAmount,
            UUID organizationId
    ) {}

    public record VoucherValidationResult(
            boolean valid,
            String code,
            BigDecimal discountAmount,
            BigDecimal finalAmount,
            String message
    ) {}
}
