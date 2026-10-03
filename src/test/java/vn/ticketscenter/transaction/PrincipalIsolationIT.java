package vn.ticketscenter.transaction;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;

import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrincipalIsolationIT {
    @Test
    void each_transaction_uses_only_its_selected_principal_factory_and_closes_it() {
        var factories = new EnumMap<DatabasePrincipal, EntityManagerFactory>(DatabasePrincipal.class);
        var buyerClosed = new AtomicBoolean();
        var adminClosed = new AtomicBoolean();
        factories.put(DatabasePrincipal.BUYER, factory(buyerClosed));
        factories.put(DatabasePrincipal.ADMIN, factory(adminClosed));
        TransactionManager transactions = new TransactionManager(factories);

        assertEquals("buyer", transactions.execute(DatabasePrincipal.BUYER, entityManager -> "buyer"));
        assertTrue(buyerClosed.get());
        assertEquals("admin", transactions.execute(DatabasePrincipal.ADMIN, entityManager -> "admin"));
        assertTrue(adminClosed.get());
    }

    private static EntityManagerFactory factory(AtomicBoolean closed) {
        EntityTransaction transaction = (EntityTransaction) Proxy.newProxyInstance(
                PrincipalIsolationIT.class.getClassLoader(), new Class<?>[]{EntityTransaction.class},
                (proxy, method, args) -> "isActive".equals(method.getName()) ? false : null);
        EntityManager entityManager = (EntityManager) Proxy.newProxyInstance(
                PrincipalIsolationIT.class.getClassLoader(), new Class<?>[]{EntityManager.class}, (proxy, method, args) -> {
                    if ("getTransaction".equals(method.getName())) return transaction;
                    if ("close".equals(method.getName())) closed.set(true);
                    return null;
                });
        return (EntityManagerFactory) Proxy.newProxyInstance(PrincipalIsolationIT.class.getClassLoader(),
                new Class<?>[]{EntityManagerFactory.class}, (proxy, method, args) ->
                        "createEntityManager".equals(method.getName()) ? entityManager : null);
    }
}
