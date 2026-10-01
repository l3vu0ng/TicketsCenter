package vn.ticketscenter.settlement;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.filter.AuthenticationFilter;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;
import vn.ticketscenter.settlement.controller.SettlementServlet;
import vn.ticketscenter.settlement.dto.SettlementDtos.SettlementSnapshot;
import vn.ticketscenter.settlement.integration.PayoutGateway;
import vn.ticketscenter.settlement.repository.SettlementRepository;
import vn.ticketscenter.settlement.service.SettlementService;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementServletTest {
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final AuthenticatedAccount ADMIN = new AuthenticatedAccount(
            UUID.randomUUID(), 0, "admin@example.test", true);

    @Test
    void adminCanRecalculateAndReceivesMoneyAsStrings() throws Exception {
        SettlementRepository repository = new SettlementRepository() {
            @Override public SettlementSnapshot recalculate(EntityManager entityManager, UUID eventId, UUID actorId) {
                return new SettlementSnapshot(UUID.randomUUID(), "DRAFT", new BigDecimal("300000"),
                        BigDecimal.ZERO, new BigDecimal("31000"), new BigDecimal("269000"));
            }
        };
        SettlementService service = new SettlementService(transactions(), repository, gateway());
        SettlementServlet servlet = new SettlementServlet(service);
        StringWriter body = new StringWriter();
        int[] status = new int[1];

        servlet.handlePost(request("/" + EVENT_ID + "/settlement/recalculate", "{}"), response(body, status));

        assertEquals(200, status[0]);
        assertTrue(body.toString().contains("\"totalCommission\":\"31000\""));
        assertTrue(body.toString().contains("\"netPayable\":\"269000\""));
    }

    private static PayoutGateway gateway() {
        return new PayoutGateway() {
            @Override public String reference(UUID payoutId) { return "SIM-" + payoutId; }
            @Override public Result submit(UUID payoutId, BigDecimal amount) { throw new AssertionError(); }
            @Override public Result query(String reference) { throw new AssertionError(); }
        };
    }

    private static HttpServletRequest request(String path, String body) {
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getPathInfo" -> path;
                    case "getReader" -> new java.io.BufferedReader(new StringReader(body));
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
