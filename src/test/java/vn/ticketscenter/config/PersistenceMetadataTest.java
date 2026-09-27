package vn.ticketscenter.config;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersistenceMetadataTest {

    @Test
    void persistenceUnitListsBusinessEntitiesAndOnlyValidatesSchema() throws Exception {
        var resource = getClass().getResourceAsStream("/META-INF/persistence.xml");
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resource);

        assertEquals(24, document.getElementsByTagName("class").getLength());
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
