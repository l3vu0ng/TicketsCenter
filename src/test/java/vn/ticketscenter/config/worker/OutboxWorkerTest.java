package vn.ticketscenter.config.worker;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutboxWorkerTest {
    @Test
    void failedEmailIsRetriedWithoutRepeatingRefundWork() {
        AtomicReference<String> status = new AtomicReference<>("PENDING");
        AtomicInteger sends = new AtomicInteger();
        OutboxWorker worker = new OutboxWorker(transactions(status), (type, payload) -> {
            if (sends.getAndIncrement() == 0) throw new IllegalStateException("mail unavailable");
        }, "test-worker");

        assertEquals(1, worker.runOnce());
        assertEquals("FAILED", status.get());
        assertEquals(1, worker.runOnce());
        assertEquals("PUBLISHED", status.get());
        assertEquals(2, sends.get());
    }

    private static TransactionManager transactions(AtomicReference<String> status) {
        UUID id = UUID.randomUUID();
        AtomicBoolean retryQueryAllowsFailed = new AtomicBoolean();
        EntityTransaction transaction = (EntityTransaction) Proxy.newProxyInstance(
                EntityTransaction.class.getClassLoader(), new Class<?>[]{EntityTransaction.class},
                (proxy, method, args) -> "isActive".equals(method.getName()));
        EntityManager entityManager = (EntityManager) Proxy.newProxyInstance(
                EntityManager.class.getClassLoader(), new Class<?>[]{EntityManager.class}, (proxy, method, args) -> {
                    if ("getTransaction".equals(method.getName())) return transaction;
                    if (!"createNativeQuery".equals(method.getName())) return null;
                    String sql = (String) args[0];
                    if (sql.startsWith("SELECT")) retryQueryAllowsFailed.set(sql.contains("'FAILED'"));
                    return Proxy.newProxyInstance(Query.class.getClassLoader(), new Class<?>[]{Query.class},
                            (query, queryMethod, queryArgs) -> switch (queryMethod.getName()) {
                                case "setParameter" -> query;
                                case "getResultList" -> status.get().equals("PENDING")
                                        || status.get().equals("FAILED") && retryQueryAllowsFailed.get()
                                        ? List.<Object[]>of(new Object[]{id, "REFUND_SUCCEEDED", "{}"}) : List.of();
                                case "executeUpdate" -> {
                                    if (sql.contains("status='PROCESSING'")) status.set("PROCESSING");
                                    if (sql.contains("status='FAILED'")) status.set("FAILED");
                                    if (sql.contains("status='PUBLISHED'")) status.set("PUBLISHED");
                                    yield 1;
                                }
                                default -> null;
                            });
                });
        EntityManagerFactory factory = (EntityManagerFactory) Proxy.newProxyInstance(
                EntityManagerFactory.class.getClassLoader(), new Class<?>[]{EntityManagerFactory.class},
                (proxy, method, args) -> "createEntityManager".equals(method.getName()) ? entityManager : null);
        return new TransactionManager(Map.of(DatabasePrincipal.WORKER, factory));
    }
}
