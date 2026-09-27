package vn.ticketscenter.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.time.Clock;
import vn.ticketscenter.identity.service.PasswordHasher;

@WebListener
public final class PersistenceListener implements ServletContextListener {

    public static final String REGISTRY_ATTRIBUTE = PersistenceRegistry.class.getName();
    private static final Logger LOGGER = Logger.getLogger(PersistenceListener.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent event) {
        AdminSeeder.validateProductionSecret(ServerConfig.getEnv(), AppConfig.get("admin.password"));
        PersistenceRegistry registry = new PersistenceRegistry();
        try {
            registry.initialize();
            new AdminSeeder(new PasswordHasher(), Clock.systemUTC()).seed(registry.transactionManager());
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Database persistence initialization failed", exception);
        }
        event.getServletContext().setAttribute(REGISTRY_ATTRIBUTE, registry);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        Object registry = event.getServletContext().getAttribute(REGISTRY_ATTRIBUTE);
        if (registry instanceof PersistenceRegistry persistenceRegistry) {
            persistenceRegistry.close();
        }
    }
}
