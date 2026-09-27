package vn.ticketscenter.config.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelMappingTest {

    private static final Map<String, String> ENTITIES = Map.ofEntries(
            Map.entry("identity.model.User", "tc_users"),
            Map.entry("identity.model.Organization", "tc_organizations"),
            Map.entry("identity.model.OrganizationMembership", "tc_organization_memberships"),
            Map.entry("identity.model.OrganizationRequest", "tc_organization_requests"),
            Map.entry("event.model.EventCategory", "tc_event_categories"),
            Map.entry("event.model.Event", "tc_events"),
            Map.entry("event.model.Zone", "tc_zones"),
            Map.entry("event.model.Seat", "tc_seats"),
            Map.entry("ticketing.model.TicketHold", "tc_ticket_holds"),
            Map.entry("ticketing.model.TicketHoldItem", "tc_ticket_hold_items"),
            Map.entry("order.model.Order", "tc_orders"),
            Map.entry("order.model.OrderItem", "tc_order_items"),
            Map.entry("order.model.Payment", "tc_payments"),
            Map.entry("order.model.Coupon", "tc_coupons"),
            Map.entry("fulfillment.model.Ticket", "tc_tickets"),
            Map.entry("fulfillment.model.CheckIn", "tc_check_ins"),
            Map.entry("fulfillment.model.RefundRequest", "tc_refund_requests"),
            Map.entry("fulfillment.model.Refund", "tc_refunds"),
            Map.entry("settlement.model.CommissionRule", "tc_commission_rules"),
            Map.entry("settlement.model.Settlement", "tc_settlements"),
            Map.entry("settlement.model.SettlementItem", "tc_settlement_items"),
            Map.entry("settlement.model.Payout", "tc_payouts"),
            Map.entry("audit.model.AuditLog", "tc_audit_logs")
    );

    @Test
    void mapsExactlyTwentyThreeBusinessEntitiesToMigrationTables() throws Exception {
        assertEquals(23, ENTITIES.size());
        for (var entry : ENTITIES.entrySet()) {
            Class<?> type = Class.forName("vn.ticketscenter." + entry.getKey());
            assertTrue(type.isAnnotationPresent(Entity.class), type.getName());
            assertEquals(entry.getValue(), type.getAnnotation(Table.class).name(), type.getName());
        }
    }
}
