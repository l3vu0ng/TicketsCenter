package vn.ticketscenter.model;

public final class ModelEnums {
    private ModelEnums() {}
    public enum UserStatus { ACTIVE, DISABLED }
    public enum OrganizationRole { MANAGER, CHECK_IN_STAFF }
    public enum OrganizationRequestStatus { PENDING, APPROVED, REJECTED }
    public enum EventStatus { DRAFT, PENDING_APPROVAL, REJECTED, PUBLISHED, CANCELLED }
    public enum ZoneType { SEATED, STANDING }
    public enum SeatStatus { AVAILABLE, HELD, SOLD }
    public enum TicketHoldStatus { ACTIVE, RELEASED, CONSUMED }
    public enum OrderStatus { PENDING_PAYMENT, PAID, CANCELLED, EXPIRED }
    public enum PaymentStatus { PENDING, CAPTURED, FAILED, UNKNOWN }
    public enum DiscountType { PERCENTAGE, FIXED_AMOUNT }
    public enum TicketStatus { ACTIVE, USED, REFUND_PENDING, REFUNDED, INVALIDATED }
    public enum CheckInResult { SUCCESS, NOT_FOUND, WRONG_EVENT, TOO_EARLY, TOO_LATE, ALREADY_USED, NOT_ACTIVE, EVENT_CANCELLED }
    public enum RefundRequestStatus { PENDING, APPROVED, REJECTED, COMPLETED }
    public enum RefundRequestReason { CUSTOMER_REQUEST, EVENT_CANCELLATION }
    public enum RefundPurpose { CUSTOMER_REFUND, PAYMENT_COMPENSATION }
    public enum RefundStatus { PENDING, SUCCEEDED, FAILED, UNKNOWN }
    public enum SettlementStatus { DRAFT, CONFIRMED, PAID }
    public enum PayoutStatus { PENDING, SUCCEEDED, FAILED }
}
