package vn.ticketscenter.fulfillment.model;

public final class FulfillmentEnums {
    private FulfillmentEnums() {}

    public enum TicketStatus { ACTIVE, USED, REFUND_PENDING, REFUNDED, INVALIDATED }
    public enum CheckInResult { SUCCESS, NOT_FOUND, WRONG_EVENT, TOO_EARLY, TOO_LATE, ALREADY_USED, NOT_ACTIVE, EVENT_CANCELLED }
    public enum RefundRequestStatus { PENDING, APPROVED, REJECTED, COMPLETED }
    public enum RefundRequestReason { CUSTOMER_REQUEST, EVENT_CANCELLATION }
    public enum RefundPurpose { CUSTOMER_REFUND, PAYMENT_COMPENSATION }
    public enum RefundStatus { PENDING, SUCCEEDED, FAILED, UNKNOWN }
}
