package vn.ticketscenter.fulfillment.controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.fulfillment.repository.OperationsRepository;
import vn.ticketscenter.fulfillment.repository.RefundRepository;
import vn.ticketscenter.fulfillment.service.OperationsService;
import vn.ticketscenter.fulfillment.service.RefundService;
import vn.ticketscenter.identity.filter.AuthenticationFilter;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RefundOperationsServletTest {
    @Test
    void buyerCannotCallRefundRetryRoute() throws Exception {
        TransactionManager transactions = transactions();
        OperationsServlet servlet = new OperationsServlet(
                new OperationsService(transactions, new OperationsRepository()),
                new RefundService(transactions, new RefundRepository()));
        int[] status = new int[1];
        StringWriter body = new StringWriter();

        servlet.doPost(request(), response(status, body));

        assertEquals(403, status[0]);
    }

    private static HttpServletRequest request() {
        AuthenticatedAccount buyer = new AuthenticatedAccount(UUID.randomUUID(), 1, "buyer@example.test", false);
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getServletPath" -> "/api/admin/refund-requests";
                    case "getPathInfo" -> "/10000000-0000-0000-0000-000000000001/retry";
                    case "getAttribute" -> AuthenticationFilter.ACCOUNT_ATTRIBUTE.equals(args[0]) ? buyer : null;
                    default -> null;
                });
    }

    private static HttpServletResponse response(int[] status, StringWriter body) {
        PrintWriter writer = new PrintWriter(body);
        return (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "setStatus" -> { status[0] = (int) args[0]; yield null; }
                    case "getWriter" -> writer;
                    default -> null;
                });
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
}
