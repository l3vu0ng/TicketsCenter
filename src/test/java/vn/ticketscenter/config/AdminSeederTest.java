package vn.ticketscenter.config;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminSeederTest {

    @Test
    void productionRejectsMissingOrDemoPassword() {
        assertThrows(IllegalStateException.class, () -> AdminSeeder.validateProductionSecret("production", null));
        assertThrows(IllegalStateException.class, () -> AdminSeeder.validateProductionSecret("production", "admin"));
        assertDoesNotThrow(() -> AdminSeeder.validateProductionSecret("development", "admin"));
    }

    @Test
    void invalidProductionSeedSecretStopsApplicationStartup() {
        System.setProperty("app.env", "production");
        System.setProperty("admin.password", "admin");
        ServletContext context = (ServletContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ServletContext.class}, (proxy, method, args) -> null);
        try {
            assertThrows(IllegalStateException.class,
                    () -> new PersistenceListener().contextInitialized(new ServletContextEvent(context)));
        } finally {
            System.clearProperty("app.env");
            System.clearProperty("admin.password");
        }
    }
}
