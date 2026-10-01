package vn.ticketscenter.fulfillment.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.fulfillment.repository.RefundRepository;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefundServiceTest {
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();

    @Test
    void buyerCannotRetryRefund() {
        FakeRepository repository = new FakeRepository();
        RefundService service = new RefundService(transactions(), repository);

        assertThrows(SecurityException.class, () -> service.retryCustomer(
                new AuthenticatedAccount(ACTOR_ID, 1, "buyer@example.com", false), TARGET_ID));
        assertEquals(0, repository.calls);
    }

    @Test
    void adminRetryUsesExplicitFailedAttemptPaths() {
        FakeRepository repository = new FakeRepository();
        RefundService service = new RefundService(transactions(), repository);
        AuthenticatedAccount admin = new AuthenticatedAccount(ACTOR_ID, 1, "admin@example.com", true);

        service.retryCustomer(admin, TARGET_ID);
        service.retryCompensation(admin, TARGET_ID);

        assertEquals(2, repository.calls);
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
        return new TransactionManager(Map.of(DatabasePrincipal.ADMIN, factory, DatabasePrincipal.WORKER, factory));
    }

    private static final class FakeRepository extends RefundRepository {
        private int calls;

        @Override
        public void retryCustomer(EntityManager entityManager, UUID requestId, UUID actorId) {
            calls++;
        }

        @Override
        public void retryCompensation(EntityManager entityManager, UUID paymentId) {
            calls++;
        }
    }
}
