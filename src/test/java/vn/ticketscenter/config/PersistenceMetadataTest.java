package vn.ticketscenter.config;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersistenceMetadataTest {

    @Test
    void persistenceUnitListsBusinessEntitiesAndOnlyValidatesSchema() throws Exception {
        var resource = getClass().getResourceAsStream("/META-INF/persistence.xml");
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resource);

        var classNodes = document.getElementsByTagName("class");
        Set<String> classes = new HashSet<>();
        for (int index = 0; index < classNodes.getLength(); index++) {
            classes.add(classNodes.item(index).getTextContent().trim());
        }

        assertEquals(classNodes.getLength(), classes.size(), "JPA entity classes must be unique");
        assertEquals(Set.of(
                "vn.ticketscenter.identity.model.User",
                "vn.ticketscenter.identity.model.Otp",
                "vn.ticketscenter.identity.model.Organization",
                "vn.ticketscenter.identity.model.OrganizationMembership",
                "vn.ticketscenter.identity.model.OrganizationRequest",
                "vn.ticketscenter.event.model.EventCategory",
                "vn.ticketscenter.event.model.Event",
                "vn.ticketscenter.event.model.Zone",
                "vn.ticketscenter.event.model.Seat",
                "vn.ticketscenter.ticketing.model.TicketHold",
                "vn.ticketscenter.ticketing.model.TicketHoldItem",
                "vn.ticketscenter.order.model.Order",
                "vn.ticketscenter.order.model.OrderItem",
                "vn.ticketscenter.order.model.Payment",
                "vn.ticketscenter.order.model.Coupon",
                "vn.ticketscenter.fulfillment.model.Ticket",
                "vn.ticketscenter.fulfillment.model.CheckIn",
                "vn.ticketscenter.fulfillment.model.RefundRequest",
                "vn.ticketscenter.fulfillment.model.Refund",
                "vn.ticketscenter.settlement.model.CommissionRule",
                "vn.ticketscenter.settlement.model.Settlement",
                "vn.ticketscenter.settlement.model.SettlementItem",
                "vn.ticketscenter.settlement.model.Payout",
                "vn.ticketscenter.audit.model.AuditLog"
        ), classes);
        assertEquals("validate", property(document, "hibernate.hbm2ddl.auto"));
        assertEquals("none", property(document, "jakarta.persistence.schema-generation.database.action"));
        assertEquals("UTC", property(document, "hibernate.jdbc.time_zone"));
    }

    private String property(org.w3c.dom.Document document, String name) {
        var properties = document.getElementsByTagName("property");
        for (int index = 0; index < properties.getLength(); index++) {
            var element = (org.w3c.dom.Element) properties.item(index);
            if (name.equals(element.getAttribute("name"))) {
                return element.getAttribute("value");
            }
        }
        return null;
    }
}
