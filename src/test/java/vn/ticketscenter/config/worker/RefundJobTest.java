package vn.ticketscenter.config.worker;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.fulfillment.repository.RefundRepository;
import vn.ticketscenter.fulfillment.repository.RefundRepository.RefundWork;
import vn.ticketscenter.payment.integration.RefundGateway;
import vn.ticketscenter.payment.integration.SimulatedRefundGateway;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RefundJobTest {
    @TempDir Path stateDirectory;

    @Test
    void restartReconcilesTimeoutAndAppliesProviderResultOnce() {
        UUID refundId = UUID.fromString("50000000-0000-0000-0000-000000000001");
        FakeRepository repository = new FakeRepository(
                new RefundWork(refundId, new BigDecimal("400000"), "PENDING", null));
        RefundJob first = new RefundJob(transactions(), repository,
                new SimulatedRefundGateway(stateDirectory, RefundGateway.Status.SUCCEEDED, true), 10);

        assertEquals(0, first.runOnce());
        assertEquals(List.of(), repository.applied);

        RefundJob restarted = new RefundJob(transactions(), repository,
                new SimulatedRefundGateway(stateDirectory, RefundGateway.Status.FAILED, false), 10);
        assertEquals(1, restarted.runOnce());
        assertEquals(List.of("SIM-" + refundId + ":SUCCEEDED"), repository.applied);
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
        return new TransactionManager(Map.of(DatabasePrincipal.WORKER, factory));
    }

    private static final class FakeRepository extends RefundRepository {
        private final RefundWork work;
        private final List<String> applied = new ArrayList<>();

        private FakeRepository(RefundWork work) {
            this.work = work;
        }

        @Override
        public List<RefundWork> findWork(EntityManager entityManager, int limit) {
            return applied.isEmpty() ? List.of(work) : List.of();
        }

        @Override
        public void apply(EntityManager entityManager, UUID refundId, RefundGateway.Result result) {
            applied.add(result.reference() + ":" + result.status());
        }
    }
}
