package vn.ticketscenter.config.worker;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.event.repository.EventCancellationRepository;
import vn.ticketscenter.event.repository.EventCancellationRepository.CancellationWork;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventCancellationJobTest {
    private static final UUID EVENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void restartContinuesOnlyUnfinishedWorkInStableBatches() {
        FakeRepository repository = new FakeRepository(List.of(
                work("HOLD", 2), work("ORDER", 3), work("ORDER", 4)));
        EventCancellationJob job = new EventCancellationJob(transactions(), repository, 2);

        assertEquals(2, job.runOnce());
        assertEquals(1, job.runOnce());
        assertEquals(List.of("HOLD:2", "ORDER:3", "ORDER:4"), repository.processed);
    }

    private static CancellationWork work(String type, int suffix) {
        return new CancellationWork(EVENT_ID, type,
                UUID.fromString("20000000-0000-0000-0000-00000000000" + suffix));
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

    private static final class FakeRepository extends EventCancellationRepository {
        private final List<CancellationWork> remaining;
        private final List<String> processed = new ArrayList<>();

        private FakeRepository(List<CancellationWork> work) {
            remaining = new ArrayList<>(work);
        }

        @Override
        public List<CancellationWork> findWork(EntityManager entityManager, int limit) {
            return remaining.stream().limit(limit).toList();
        }

        @Override
        public void process(EntityManager entityManager, CancellationWork work) {
            processed.add(work.type() + ":" + work.id().toString().charAt(35));
            remaining.remove(work);
        }

        @Override
        public void completeFinishedEvents(EntityManager entityManager) {
        }
    }
}
