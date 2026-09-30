package vn.ticketscenter.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CouponService {
    private CouponService() {}

    public static BigDecimal discount(BigDecimal subtotal, String type, BigDecimal percentage, BigDecimal fixedAmount) {
        if (subtotal == null || subtotal.signum() < 0 || type == null) return null;
        BigDecimal cap = subtotal.multiply(new BigDecimal("0.30")).setScale(0, RoundingMode.FLOOR);
        if ("PERCENTAGE".equals(type) && percentage != null && percentage.signum() > 0
                && percentage.compareTo(new BigDecimal("30")) <= 0 && fixedAmount == null) {
            return subtotal.multiply(percentage).divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR).min(cap);
        }
        if ("FIXED_AMOUNT".equals(type) && fixedAmount != null && fixedAmount.signum() > 0 && percentage == null) {
            return fixedAmount.min(cap);
        }
        return null;
    }
}
