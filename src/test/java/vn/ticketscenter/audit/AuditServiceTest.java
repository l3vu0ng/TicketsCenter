package vn.ticketscenter.audit;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.audit.repository.AuditRepository;
import vn.ticketscenter.audit.repository.AuditRepository.AuditFilter;
import vn.ticketscenter.audit.service.AuditService;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditServiceTest {
    @Test
    void auditReadRequiresCurrentAdministrator() {
        AuditService service = new AuditService(new TransactionManager(Map.of()), new AuditRepository());
        AuthenticatedAccount buyer = new AuthenticatedAccount(UUID.randomUUID(), 0, "buyer@example.test", false);

        assertThrows(SecurityException.class, () -> service.get(buyer,
                new AuditFilter(null, null, Instant.EPOCH, Instant.EPOCH.plusSeconds(1), 1, 20)));
        assertThrows(SecurityException.class, () -> service.getOne(buyer, 1));
    }
}
