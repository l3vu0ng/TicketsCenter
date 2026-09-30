package vn.ticketscenter.order.dto;

import vn.ticketscenter.order.model.OrderEnums;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class OrderDtos {
    private OrderDtos() {}

    public record CreateOrderFromHoldRequest(
            UUID holdId,
            String couponCode
    ) {}

    public record OrderSummaryDto(
            UUID id,
            UUID eventId,
            UUID userId,
            BigDecimal originalTotal,
            BigDecimal discountAmount,
            BigDecimal finalAmount,
            OrderEnums.OrderStatus status,
            Instant createdAt
    ) {}

    public record OrderResult(
            UUID orderId, String orderCode, BigDecimal subtotal, BigDecimal discount,
            BigDecimal total, String status, UUID couponId
    ) {}
}
