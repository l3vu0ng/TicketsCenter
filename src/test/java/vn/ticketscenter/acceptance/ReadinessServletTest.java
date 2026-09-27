package vn.ticketscenter.acceptance;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.controller.ReadinessServlet;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReadinessServletTest {

    @Test
    void returns503WithoutLeakingDatabaseDetails() throws Exception {
        StringWriter body = new StringWriter();
        int[] status = {200};
        var response = (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "setStatus" -> { status[0] = (int) args[0]; yield null; }
                    case "getWriter" -> new PrintWriter(body);
                    default -> null;
                });

        new ReadinessServlet(() -> false).service(
                (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) ->
                                "getMethod".equals(method.getName()) ? "GET" : null), response);

        assertEquals(503, status[0]);
        assertEquals("{\"error\":{\"code\":\"DEPENDENCY_UNAVAILABLE\"}}", body.toString());
    }
}
