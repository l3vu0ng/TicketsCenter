package vn.ticketscenter.settlement.model;

public final class SettlementEnums {
    private SettlementEnums() {}

    public enum SettlementStatus { DRAFT, CONFIRMED, PAID }
    public enum PayoutStatus { PENDING, SUCCEEDED, FAILED }
}
