package vn.ticketscenter.config;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.util.InputParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InputParserTest {

    @Test
    void rejectsMalformedUuidAndInvalidVndBeforePersistence() {
        assertThrows(IllegalArgumentException.class, () -> InputParser.uuid("not-a-uuid", "eventId"));
        assertThrows(IllegalArgumentException.class, () -> InputParser.vnd("1.5", "amount"));
        assertThrows(IllegalArgumentException.class, () -> InputParser.vnd("10000000000000000000", "amount"));
        assertEquals("1000", InputParser.vnd("1000", "amount").toPlainString());
    }
}
