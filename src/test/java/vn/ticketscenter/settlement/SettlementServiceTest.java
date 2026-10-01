package vn.ticketscenter.settlement;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;
import vn.ticketscenter.settlement.dto.SettlementDtos.PayoutBalance;
import vn.ticketscenter.settlement.integration.PayoutGateway;
import vn.ticketscenter.settlement.repository.SettlementRepository;
import vn.ticketscenter.settlement.service.SettlementService;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettlementServiceTest {
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID SETTLEMENT_ID = UUID.randomUUID();
    private static final UUID PAYOUT_ID = UUID.randomUUID();

    @Test
    void payoutReservesBeforeCallingGatewayThenRecordsVerifiedOutcome() {
        FakeRepository repository = new FakeRepository();
        PayoutGateway gateway = new PayoutGateway() {
            @Override public String reference(UUID payoutId) { return "SIM-" + payoutId; }
            @Override public Result submit(UUID payoutId, BigDecimal amount) {
                assertEquals(List.of("PENDING"), repository.results);
                return new Result(payoutId, amount, reference(payoutId), Status.SUCCEEDED);
            }
            @Override public Result query(String reference) { throw new UnsupportedOperationException(); }
        };
        SettlementService service = new SettlementService(transactions(), repository, gateway);

        service.payout(admin(), SETTLEMENT_ID, PAYOUT_ID, new BigDecimal("300000"));

        assertEquals(List.of("PENDING", "SUCCEEDED"), repository.results);
    }

    @Test
    void buyerCannotInitiatePayout() {
        FakeRepository repository = new FakeRepository();
        SettlementService service = new SettlementService(transactions(), repository, new PayoutGateway() {
            @Override public String reference(UUID payoutId) { return "unused"; }
            @Override public Result submit(UUID payoutId, BigDecimal amount) { throw new AssertionError(); }
            @Override public Result query(String reference) { throw new AssertionError(); }
        });
        AuthenticatedAccount buyer = new AuthenticatedAccount(ACTOR_ID, 0, "buyer@example.test", false);

        assertThrows(SecurityException.class,
                () -> service.payout(buyer, SETTLEMENT_ID, PAYOUT_ID, BigDecimal.ONE));
        assertEquals(List.of(), repository.results);
    }

    private static AuthenticatedAccount admin() {
        return new AuthenticatedAccount(ACTOR_ID, 0, "admin@example.test", true);
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

    private static final class FakeRepository extends SettlementRepository {
        private final List<String> results = new ArrayList<>();

        @Override
        public PayoutBalance recordPayout(EntityManager entityManager, UUID settlementId, UUID payoutId,
                                          UUID actorId, BigDecimal amount, String reference, String result) {
            results.add(result);
            return new PayoutBalance(settlementId, UUID.randomUUID(), BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, new BigDecimal("300000"), result, null, BigDecimal.ZERO,
                    BigDecimal.ZERO, new BigDecimal("300000"), new BigDecimal("300000"));
        }
    }
}
