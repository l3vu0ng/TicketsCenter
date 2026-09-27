package vn.ticketscenter.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelMappingTest {

    private static final Map<String, String> ENTITIES = Map.ofEntries(
            Map.entry("identity.User", "tc_users"),
            Map.entry("identity.Organization", "tc_organizations"),
            Map.entry("identity.OrganizationMembership", "tc_organization_memberships"),
            Map.entry("identity.OrganizationRequest", "tc_organization_requests"),
            Map.entry("event.EventCategory", "tc_event_categories"),
            Map.entry("event.Event", "tc_events"),
            Map.entry("event.Zone", "tc_zones"),
            Map.entry("event.Seat", "tc_seats"),
            Map.entry("ticketing.TicketHold", "tc_ticket_holds"),
            Map.entry("ticketing.TicketHoldItem", "tc_ticket_hold_items"),
            Map.entry("order.Order", "tc_orders"),
            Map.entry("order.OrderItem", "tc_order_items"),
            Map.entry("order.Payment", "tc_payments"),
            Map.entry("order.Coupon", "tc_coupons"),
            Map.entry("fulfillment.Ticket", "tc_tickets"),
            Map.entry("fulfillment.CheckIn", "tc_check_ins"),
            Map.entry("fulfillment.RefundRequest", "tc_refund_requests"),
            Map.entry("fulfillment.Refund", "tc_refunds"),
            Map.entry("settlement.CommissionRule", "tc_commission_rules"),
            Map.entry("settlement.Settlement", "tc_settlements"),
            Map.entry("settlement.SettlementItem", "tc_settlement_items"),
            Map.entry("settlement.Payout", "tc_payouts"),
            Map.entry("audit.AuditLog", "tc_audit_logs")
    );

    @Test
    void mapsExactlyTwentyThreeBusinessEntitiesToMigrationTables() throws Exception {
        assertEquals(23, ENTITIES.size());
        for (var entry : ENTITIES.entrySet()) {
            Class<?> type = Class.forName("vn.ticketscenter.model." + entry.getKey());
            assertTrue(type.isAnnotationPresent(Entity.class), type.getName());
            assertEquals(entry.getValue(), type.getAnnotation(Table.class).name(), type.getName());
        }
    }
}
