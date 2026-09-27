package vn.ticketscenter.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.service.identity.SessionService;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SessionServiceTest {

    @Test
    void loginInvalidatesOldSessionAndKeepsOnlyIdentity() {
        SessionState oldSession = new SessionState();
        oldSession.values.put("attacker", "value");
        SessionState newSession = new SessionState();
        HttpServletRequest request = request(oldSession, newSession);
        UUID userId = UUID.randomUUID();

        new SessionService().login(request, userId, 7);

        assertTrue(oldSession.invalidated);
        assertEquals(userId, newSession.values.get(SessionService.USER_ID));
        assertEquals(7, newSession.values.get(SessionService.AUTH_VERSION));
        assertFalse(newSession.values.containsKey("attacker"));
    }

    @Test
    void logoutInvalidatesOnlyCurrentSession() {
        SessionState current = new SessionState();
        SessionState otherDevice = new SessionState();
        new SessionService().logout(request(current, new SessionState()));
        assertTrue(current.invalidated);
        assertFalse(otherDevice.invalidated);
    }

    private HttpServletRequest request(SessionState current, SessionState created) {
        return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> {
                    if (!"getSession".equals(method.getName())) return null;
                    if (args == null || args.length == 0 || Boolean.TRUE.equals(args[0])) {
                        return current.invalidated ? created.proxy() : current.proxy();
                    }
                    return current.invalidated ? null : current.proxy();
                });
    }

    private static final class SessionState {
        private final Map<String, Object> values = new HashMap<>();
        private boolean invalidated;

        HttpSession proxy() {
            return (HttpSession) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{HttpSession.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "setAttribute" -> { values.put((String) args[0], args[1]); yield null; }
                        case "getAttribute" -> values.get(args[0]);
                        case "invalidate" -> { invalidated = true; values.clear(); yield null; }
                        default -> null;
                    });
        }
    }
}
