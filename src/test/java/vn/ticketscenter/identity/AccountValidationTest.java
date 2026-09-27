package vn.ticketscenter.identity;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.service.identity.AccountService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountValidationTest {

    @Test
    void normalizesOnlyEmailWhitespaceAndCase() {
        assertEquals("person@example.com", AccountService.normalizeEmail(" Person@Example.COM "));
    }

    @Test
    void rejectsInvalidEmailAndWeakOrOversizedPassword() {
        assertThrows(IllegalArgumentException.class, () -> AccountService.normalizeEmail("not-an-email"));
        assertThrows(IllegalArgumentException.class, () -> AccountService.validatePassword("short"));
        assertThrows(IllegalArgumentException.class, () -> AccountService.validatePassword("x".repeat(257)));
    }
}
