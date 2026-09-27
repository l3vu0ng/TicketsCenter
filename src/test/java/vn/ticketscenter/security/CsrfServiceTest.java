package vn.ticketscenter.security;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.service.identity.CsrfService;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CsrfServiceTest {

    @Test
    void tokenIsStablePerSessionAndComparedExactly() {
        Map<String, Object> attributes = new HashMap<>();
        HttpSession session = (HttpSession) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpSession.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getAttribute" -> attributes.get(args[0]);
                    case "setAttribute" -> { attributes.put((String) args[0], args[1]); yield null; }
                    default -> null;
                });
        CsrfService csrf = new CsrfService();

        String token = csrf.token(session);

        assertEquals(token, csrf.token(session));
        assertTrue(csrf.matches(session, token));
        assertFalse(csrf.matches(session, token + "x"));
        assertFalse(csrf.matches(session, null));
    }
}
