package vn.ticketscenter.event;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.event.controller.EventCancellationServlet;
import vn.ticketscenter.event.dto.EventCancellationDtos.CancellationProgress;
import vn.ticketscenter.event.repository.EventCancellationRepository;
import vn.ticketscenter.event.service.EventCancellationService;
import vn.ticketscenter.identity.filter.AuthenticationFilter;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventCancellationServletTest {
    private static final UUID EVENT_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final AuthenticatedAccount ADMIN = new AuthenticatedAccount(
            UUID.fromString("30000000-0000-0000-0000-000000000002"), 0, "admin@example.test", true);

    @Test
    void adminRouteReturnsCancellationProgressWithoutExposingBuyerData() throws Exception {
        CancellationProgress progress = new CancellationProgress(
                EVENT_ID, "CANCELLED", 2, 1, 0, 1, 1, 0, 1, List.of());
        EventCancellationRepository repository = new EventCancellationRepository() {
            @Override public void cancel(EntityManager entityManager, UUID eventId, UUID actorId) {}
            @Override public CancellationProgress progress(EntityManager entityManager, UUID eventId) { return progress; }
        };
        EventCancellationServlet servlet = new EventCancellationServlet(
                new EventCancellationService(transactions(), repository));
        StringWriter body = new StringWriter();
        int[] status = new int[1];

        servlet.handlePost(request("/events/" + EVENT_ID + "/cancel"), response(body, status));

        assertEquals(200, status[0]);
        assertTrue(body.toString().contains("\"status\":\"CANCELLED\""));
        assertTrue(body.toString().contains("\"exceptionCount\":1"));
        assertTrue(!body.toString().contains("buyer"));
    }

    private static HttpServletRequest request(String path) {
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getPathInfo" -> path;
                    case "getAttribute" -> AuthenticationFilter.ACCOUNT_ATTRIBUTE.equals(args[0]) ? ADMIN : null;
                    default -> null;
                });
    }

    private static HttpServletResponse response(StringWriter body, int[] status) {
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
        return new TransactionManager(Map.of(DatabasePrincipal.ADMIN, factory));
    }
}
