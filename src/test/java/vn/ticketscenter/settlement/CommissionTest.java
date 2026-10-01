package vn.ticketscenter.settlement;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.settlement.model.CommissionRule;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommissionTest {
    @Test
    void calculatesWholeVndCommissionFromRemainingAmount() {
        assertEquals(new BigDecimal("31000"), CommissionRule.calculateFee(
                new BigDecimal("300000"), new BigDecimal("10"), new BigDecimal("1000")));
        assertEquals(BigDecimal.ZERO, CommissionRule.calculateFee(
                BigDecimal.ZERO, new BigDecimal("10"), new BigDecimal("1000")));
        assertEquals(new BigDecimal("300000"), CommissionRule.calculateFee(
                new BigDecimal("300000"), BigDecimal.ZERO, new BigDecimal("400000")));
        assertEquals(new BigDecimal("2"), CommissionRule.calculateFee(
                new BigDecimal("10"), new BigDecimal("15"), BigDecimal.ZERO));
    }

    @Test
    void rejectsInvalidCommissionInputsLikeSqlFunction() {
        assertThrows(IllegalArgumentException.class,
                () -> CommissionRule.calculateFee(null, BigDecimal.TEN, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> CommissionRule.calculateFee(BigDecimal.ONE, new BigDecimal("-1"), BigDecimal.ZERO));
    }
}
