package vn.ticketscenter.payment.dto;

import vn.ticketscenter.order.model.OrderEnums;

import java.math.BigDecimal;
import java.util.UUID;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record PaymentInitiateRequest(
            UUID orderId,
            BigDecimal amount,
            String bankCode,
            String language
    ) {}

    public record PaymentInitiateResponse(
            String code,
            String message,
            String paymentUrl,
            String txnRef
    ) {}

    public record PaymentCallbackResult(
            boolean validSignature,
            String txnRef,
            String responseCode,
            OrderEnums.PaymentStatus status,
            String message
    ) {}
}
