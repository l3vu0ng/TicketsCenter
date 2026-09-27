package vn.ticketscenter.config.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionManagerTest {

    @Test
    void commitsAndClosesOneEntityManager() {
        Recorder recorder = new Recorder();
        TransactionManager manager = new TransactionManager(Map.of(DatabasePrincipal.BUYER, recorder.factory()));

        String result = manager.execute(DatabasePrincipal.BUYER, entityManager -> "ok");

        assertEquals("ok", result);
        assertEquals(List.of("begin", "flush", "commit", "close"), recorder.calls);
    }

    @Test
    void rollsBackAndClosesWhenCallbackFails() {
        Recorder recorder = new Recorder();
        TransactionManager manager = new TransactionManager(Map.of(DatabasePrincipal.BUYER, recorder.factory()));

        assertThrows(IllegalStateException.class, () -> manager.execute(DatabasePrincipal.BUYER, entityManager -> {
            throw new IllegalStateException("boom");
        }));

        assertEquals(List.of("begin", "rollback", "close"), recorder.calls);
    }

    private static final class Recorder {
        private final List<String> calls = new ArrayList<>();
        private boolean active;

        EntityManagerFactory factory() {
            EntityTransaction transaction = (EntityTransaction) Proxy.newProxyInstance(
                    EntityTransaction.class.getClassLoader(), new Class<?>[]{EntityTransaction.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "begin" -> { active = true; calls.add("begin"); yield null; }
                        case "commit" -> { active = false; calls.add("commit"); yield null; }
                        case "rollback" -> { active = false; calls.add("rollback"); yield null; }
                        case "isActive" -> active;
                        default -> null;
                    });
            EntityManager entityManager = (EntityManager) Proxy.newProxyInstance(
                    EntityManager.class.getClassLoader(), new Class<?>[]{EntityManager.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getTransaction" -> transaction;
                        case "flush" -> { calls.add("flush"); yield null; }
                        case "close" -> { calls.add("close"); yield null; }
                        default -> null;
                    });
            return (EntityManagerFactory) Proxy.newProxyInstance(
                    EntityManagerFactory.class.getClassLoader(), new Class<?>[]{EntityManagerFactory.class},
                    (proxy, method, args) -> "createEntityManager".equals(method.getName()) ? entityManager : null);
        }
    }
}
