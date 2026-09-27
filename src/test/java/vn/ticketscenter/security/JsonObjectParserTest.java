package vn.ticketscenter.security;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.web.JsonObjectParser;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonObjectParserTest {

    @Test
    void parsesFlatAuthPayloadWithoutChangingPassword() throws Exception {
        var result = JsonObjectParser.parse(new StringReader(
                "{\"email\":\" Person@Example.com \",\"password\":\" A\\\"b\\\\C 123456 \"}"));
        assertEquals(" Person@Example.com ", result.get("email"));
        assertEquals(" A\"b\\C 123456 ", result.get("password"));
    }

    @Test
    void rejectsDuplicateAndNestedFields() {
        assertThrows(IllegalArgumentException.class, () -> JsonObjectParser.parse(new StringReader("{\"a\":\"1\",\"a\":\"2\"}")));
        assertThrows(IllegalArgumentException.class, () -> JsonObjectParser.parse(new StringReader("{\"a\":{}}")));
    }
}
