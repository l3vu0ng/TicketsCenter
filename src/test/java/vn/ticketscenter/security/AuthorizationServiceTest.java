package vn.ticketscenter.security;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.service.identity.AccountService;
import vn.ticketscenter.service.identity.AuthorizationService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthorizationServiceTest {

    @Test
    void ownerAndAdminGuardsUseServerIdentity() {
        UUID actor = UUID.randomUUID();
        assertDoesNotThrow(() -> AuthorizationService.requireOwner(actor, actor));
        assertThrows(SecurityException.class, () -> AuthorizationService.requireOwner(actor, UUID.randomUUID()));
        assertDoesNotThrow(() -> AuthorizationService.requireAdmin(
                new AccountService.AuthenticatedAccount(actor, 0, "admin@example.com", true)));
        assertThrows(SecurityException.class, () -> AuthorizationService.requireAdmin(
                new AccountService.AuthenticatedAccount(actor, 0, "buyer@example.com", false)));
    }
}
