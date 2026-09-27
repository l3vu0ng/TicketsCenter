package vn.ticketscenter.order.model;

public final class OrderEnums {
    private OrderEnums() {}

    public enum OrderStatus { PENDING_PAYMENT, PAID, CANCELLED, EXPIRED }
    public enum PaymentStatus { PENDING, CAPTURED, FAILED, UNKNOWN }
    public enum DiscountType { PERCENTAGE, FIXED_AMOUNT }
}
