package vn.ticketscenter.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.filter.CsrfFilter;
import vn.ticketscenter.config.web.RequestValidationFilter;

import java.lang.reflect.Proxy;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

class HttpSecurityTest {

    @Test
    void postWithoutCsrfIsRejectedBeforeChain() throws Exception {
        int[] status = {200};
        boolean[] called = {false};
        new CsrfFilter().doFilter(request("POST", 10, "application/json"), response(status), chain(called));
        assertEquals(403, status[0]);
        assertFalse(called[0]);
    }

    @Test
    void oversizedPayloadIsRejectedBeforeChain() throws Exception {
        int[] status = {200};
        boolean[] called = {false};
        new RequestValidationFilter().doFilter(request("POST", 70_000, "application/json"), response(status), chain(called));
        assertEquals(413, status[0]);
        assertFalse(called[0]);
    }

    private HttpServletRequest request(String method, long length, String contentType) {
        return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, called, args) -> switch (called.getName()) {
                    case "getMethod" -> method;
                    case "getContentLengthLong" -> length;
                    case "getContentType" -> contentType;
                    case "getSession" -> null;
                    default -> null;
                });
    }

    private HttpServletResponse response(int[] status) {
        return (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, (proxy, method, args) -> {
                    if ("setStatus".equals(method.getName())) status[0] = (int) args[0];
                    if ("getWriter".equals(method.getName())) return new PrintWriter(new StringWriter());
                    return null;
                });
    }

    private FilterChain chain(boolean[] called) {
        return (request, response) -> called[0] = true;
    }
}
