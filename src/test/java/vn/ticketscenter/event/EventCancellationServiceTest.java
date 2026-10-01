package vn.ticketscenter.event;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.event.dto.EventCancellationDtos.CancellationProgress;
import vn.ticketscenter.event.repository.EventCancellationRepository;
import vn.ticketscenter.event.service.EventCancellationService;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventCancellationServiceTest {
    private static final UUID ADMIN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID EVENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final AuthenticatedAccount ADMIN = new AuthenticatedAccount(ADMIN_ID, 0, "admin@example.test", true);
    private static final AuthenticatedAccount MANAGER = new AuthenticatedAccount(ADMIN_ID, 0, "manager@example.test", false);

    @Test
    void adminCanCancelAndReadProgress() {
        FakeRepository repository = new FakeRepository();
        EventCancellationService service = new EventCancellationService(transactions(), repository);

        CancellationProgress cancelled = service.cancel(ADMIN, EVENT_ID);
        CancellationProgress current = service.progress(ADMIN, EVENT_ID);

        assertEquals("CANCELLED", cancelled.status());
        assertEquals(cancelled, current);
        assertEquals(ADMIN_ID, repository.actorId);
    }

    @Test
    void managerCannotCancelOrReadProgress() {
        EventCancellationService service = new EventCancellationService(transactions(), new FakeRepository());

        assertThrows(SecurityException.class, () -> service.cancel(MANAGER, EVENT_ID));
        assertThrows(SecurityException.class, () -> service.progress(MANAGER, EVENT_ID));
    }

    private static TransactionManager transactions() {
        EntityTransaction transaction = (EntityTransaction) Proxy.newProxyInstance(
                EntityTransaction.class.getClassLoader(), new Class<?>[]{EntityTransaction.class},
                (proxy, method, args) -> "isActive".equals(method.getName()));
        EntityManager entityManager = (EntityManager) Proxy.newProxyInstance(
                EntityManager.class.getClassLoader(), new Class<?>[]{EntityManager.class},
                (proxy, method, args) -> "getTransaction".equals(method.getName()) ? transaction : null);
        EntityManagerFactory factory = (EntityManagerFactory) Proxy.newProxyInstance(
                EntityManagerFactory.class.getClassLoader(), new Class<?>[]{EntityManagerFactory.class},
                (proxy, method, args) -> "createEntityManager".equals(method.getName()) ? entityManager : null);
        return new TransactionManager(Map.of(DatabasePrincipal.ADMIN, factory));
    }

    private static final class FakeRepository extends EventCancellationRepository {
        private UUID actorId;
        private final CancellationProgress progress = new CancellationProgress(
                EVENT_ID, "CANCELLED", 3, 1, 1, 1, 2, 1, 0, List.of());

        @Override
        public void cancel(EntityManager entityManager, UUID eventId, UUID actorId) {
            this.actorId = actorId;
        }

        @Override
        public CancellationProgress progress(EntityManager entityManager, UUID eventId) {
            return progress;
        }
    }
}
