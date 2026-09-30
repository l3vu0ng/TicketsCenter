package vn.ticketscenter.order;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;
import vn.ticketscenter.order.dto.OrderDtos.OrderResult;
import vn.ticketscenter.order.repository.OrderRepository;
import vn.ticketscenter.order.service.CouponService;
import vn.ticketscenter.order.service.OrderService;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {
    private static final UUID ACTOR = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID HOLD = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID ORDER = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final AuthenticatedAccount ACCOUNT = new AuthenticatedAccount(ACTOR, 0, "buyer@example.test", false);

    @Test
    void createsOrderUsingAuthenticatedActorAndKeepsServerBreakdown() {
        FakeRepository repository = new FakeRepository();
        OrderService service = new OrderService(transactions(), repository);

        OrderResult result = service.createFromHold(ACCOUNT, HOLD);

        assertEquals(ORDER, result.orderId());
        assertEquals(ACTOR, repository.actorId);
        assertEquals(HOLD, repository.holdId);
        assertEquals(new BigDecimal("500000"), result.subtotal());
        assertEquals(BigDecimal.ZERO, result.discount());
    }

    @Test
    void couponCodeBlankMeansRemoveAndMoneyAlwaysUsesThirtyPercentCeiling() {
        FakeRepository repository = new FakeRepository();
        OrderService service = new OrderService(transactions(), repository);

        service.applyCoupon(ACCOUNT, ORDER, "   ");

        assertNull(repository.couponCode);
        assertEquals(new BigDecimal("150000"), CouponService.discount(
                new BigDecimal("500000"), "FIXED_AMOUNT", null, new BigDecimal("200000")));
        assertEquals(BigDecimal.ZERO, CouponService.discount(
                BigDecimal.ONE, "PERCENTAGE", new BigDecimal("30"), null));
    }

    @Test
    void unauthenticatedOrderMutationIsRejected() {
        OrderService service = new OrderService(transactions(), new FakeRepository());
        assertThrows(SecurityException.class, () -> service.createFromHold(null, HOLD));
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
        return new TransactionManager(Map.of(DatabasePrincipal.BUYER, factory));
    }

    private static final class FakeRepository extends OrderRepository {
        private UUID actorId;
        private UUID holdId;
        private UUID orderId;
        private String couponCode;

        @Override
        public OrderResult createFromHold(EntityManager entityManager, UUID holdId, UUID actorId) {
            this.holdId = holdId;
            this.actorId = actorId;
            return new OrderResult(ORDER, "ORD-TEST", new BigDecimal("500000"), BigDecimal.ZERO,
                    new BigDecimal("500000"), "PENDING_PAYMENT", null);
        }

        @Override
        public OrderResult applyCoupon(EntityManager entityManager, UUID orderId, UUID actorId, String couponCode) {
            this.orderId = orderId;
            this.actorId = actorId;
            this.couponCode = couponCode;
            return new OrderResult(ORDER, "ORD-TEST", new BigDecimal("500000"), BigDecimal.ZERO,
                    new BigDecimal("500000"), "PENDING_PAYMENT", null);
        }
    }
}
