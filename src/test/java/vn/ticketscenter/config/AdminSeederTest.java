package vn.ticketscenter.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminSeederTest {

    @Test
    void productionRejectsMissingOrDemoPassword() {
        assertThrows(IllegalStateException.class, () -> AdminSeeder.validateProductionSecret("production", null));
        assertThrows(IllegalStateException.class, () -> AdminSeeder.validateProductionSecret("production", "admin"));
        assertDoesNotThrow(() -> AdminSeeder.validateProductionSecret("development", "admin"));
    }
}
