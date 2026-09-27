package vn.ticketscenter.order;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.order.model.OrderEnums;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.order.dto.VoucherDtos.VoucherValidationRequest;
import vn.ticketscenter.order.dto.VoucherDtos.VoucherValidationResult;
import vn.ticketscenter.order.model.Coupon;
import vn.ticketscenter.order.repository.CouponRepository;
import vn.ticketscenter.order.service.VoucherService;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VoucherService Unit Tests")
class VoucherServiceTest {

    private static TransactionManager createTestTransactionManager() {
        EntityTransaction transaction = (EntityTransaction) Proxy.newProxyInstance(
                EntityTransaction.class.getClassLoader(), new Class<?>[]{EntityTransaction.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isActive" -> false;
                    default -> null;
                });
        EntityManager entityManager = (EntityManager) Proxy.newProxyInstance(
                EntityManager.class.getClassLoader(), new Class<?>[]{EntityManager.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getTransaction" -> transaction;
                    default -> null;
                });
        EntityManagerFactory factory = (EntityManagerFactory) Proxy.newProxyInstance(
                EntityManagerFactory.class.getClassLoader(), new Class<?>[]{EntityManagerFactory.class},
                (proxy, method, args) -> "createEntityManager".equals(method.getName()) ? entityManager : null);
        return new TransactionManager(Map.of(DatabasePrincipal.BUYER, factory));
    }

    private static class StubCouponRepository extends CouponRepository {
        Coupon couponToReturn;

        @Override
        public Optional<Coupon> findByCode(EntityManager em, String code) {
            if (couponToReturn != null && couponToReturn.getCode().equalsIgnoreCase(code)) {
                return Optional.of(couponToReturn);
            }
            return Optional.empty();
        }
    }

    @Test
    @DisplayName("Empty or blank voucher code is rejected")
    void testBlankCodeRejected() {
        VoucherService service = new VoucherService(createTestTransactionManager(), new StubCouponRepository());
        VoucherValidationResult result = service.validateAndCalculate(new VoucherValidationRequest("", new BigDecimal("100000"), null));
        assertFalse(result.valid());
        assertEquals("Mã voucher không được để trống", result.message());
    }

    @Test
    @DisplayName("Invalid order amount is rejected")
    void testInvalidOrderAmountRejected() {
        VoucherService service = new VoucherService(createTestTransactionManager(), new StubCouponRepository());
        VoucherValidationResult result = service.validateAndCalculate(new VoucherValidationRequest("DISCOUNT", BigDecimal.ZERO, null));
        assertFalse(result.valid());
        assertEquals("Giá trị đơn hàng không hợp lệ", result.message());
    }

    @Test
    @DisplayName("Non-existent voucher code returns error")
    void testNotFoundVoucher() {
        StubCouponRepository repo = new StubCouponRepository();
        repo.couponToReturn = null;
        VoucherService service = new VoucherService(createTestTransactionManager(), repo);

        VoucherValidationResult result = service.validateAndCalculate(
                new VoucherValidationRequest("NONEXISTENT", new BigDecimal("100000"), null));
        assertFalse(result.valid());
        assertEquals("Mã voucher không tồn tại", result.message());
    }

    @Test
    @DisplayName("Inactive coupon is rejected")
    void testInactiveCoupon() {
        StubCouponRepository repo = new StubCouponRepository();
        repo.couponToReturn = new Coupon(UUID.randomUUID(), null, "INACTIVE",
                OrderEnums.DiscountType.PERCENTAGE, new BigDecimal("10"), null, null, 100,
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS), false);

        VoucherService service = new VoucherService(createTestTransactionManager(), repo);
        VoucherValidationResult result = service.validateAndCalculate(
                new VoucherValidationRequest("INACTIVE", new BigDecimal("100000"), null));
        assertFalse(result.valid());
        assertEquals("Mã voucher đã bị vô hiệu hóa", result.message());
    }

    @Test
    @DisplayName("Expired coupon is rejected")
    void testExpiredCoupon() {
        StubCouponRepository repo = new StubCouponRepository();
        repo.couponToReturn = new Coupon(UUID.randomUUID(), null, "EXPIRED",
                OrderEnums.DiscountType.PERCENTAGE, new BigDecimal("10"), null, null, 100,
                Instant.now().minus(10, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS), true);

        VoucherService service = new VoucherService(createTestTransactionManager(), repo);
        VoucherValidationResult result = service.validateAndCalculate(
                new VoucherValidationRequest("EXPIRED", new BigDecimal("100000"), null));
        assertFalse(result.valid());
        assertEquals("Mã voucher đã hết hạn", result.message());
    }

    @Test
    @DisplayName("Percentage coupon calculates discount and respects maxDiscountAmount")
    void testPercentageCouponWithCap() {
        StubCouponRepository repo = new StubCouponRepository();
        repo.couponToReturn = new Coupon(UUID.randomUUID(), null, "SALE20",
                OrderEnums.DiscountType.PERCENTAGE, new BigDecimal("20.00"), null, new BigDecimal("30000"), 100,
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS), true);

        VoucherService service = new VoucherService(createTestTransactionManager(), repo);
        // Order 200,000 * 20% = 40,000 -> capped at 30,000
        VoucherValidationResult result = service.validateAndCalculate(
                new VoucherValidationRequest("SALE20", new BigDecimal("200000"), null));
        assertTrue(result.valid());
        assertEquals(new BigDecimal("30000"), result.discountAmount());
        assertEquals(new BigDecimal("170000"), result.finalAmount());
    }

    @Test
    @DisplayName("Fixed amount coupon calculates discount and does not exceed order amount")
    void testFixedAmountCoupon() {
        StubCouponRepository repo = new StubCouponRepository();
        repo.couponToReturn = new Coupon(UUID.randomUUID(), null, "MINUS50K",
                OrderEnums.DiscountType.FIXED_AMOUNT, null, new BigDecimal("50000"), null, 100,
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS), true);

        VoucherService service = new VoucherService(createTestTransactionManager(), repo);
        // Order 200,000 -> discount 50,000 -> final 150,000
        VoucherValidationResult result = service.validateAndCalculate(
                new VoucherValidationRequest("MINUS50K", new BigDecimal("200000"), null));
        assertTrue(result.valid());
        assertEquals(new BigDecimal("50000"), result.discountAmount());
        assertEquals(new BigDecimal("150000"), result.finalAmount());

        // Order 30,000 (< 50,000) -> discount 30,000 -> final 0
        VoucherValidationResult result2 = service.validateAndCalculate(
                new VoucherValidationRequest("MINUS50K", new BigDecimal("30000"), null));
        assertTrue(result2.valid());
        assertEquals(new BigDecimal("30000"), result2.discountAmount());
        assertEquals(BigDecimal.ZERO, result2.finalAmount());
    }
}
