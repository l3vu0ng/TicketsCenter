package vn.ticketscenter.identity;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.service.PasswordHasher;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void storesSaltAndParametersWithoutPlaintext() {
        String first = hasher.hash("Correct Horse Battery Staple");
        String second = hasher.hash("Correct Horse Battery Staple");

        assertNotEquals(first, second);
        assertFalse(first.contains("Correct Horse"));
        assertTrue(hasher.matches("Correct Horse Battery Staple", first));
        assertFalse(hasher.matches("wrong", first));
        assertFalse(hasher.matches(null, first));
    }

    @Test
    void rejectsMalformedStoredHash() {
        assertFalse(hasher.matches("password", "broken"));
    }
}
