package vn.ticketscenter.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.controller.HealthServlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Day 01 Servlet Contract Test — container deployment is verified by scripts/verify-tomcat.sh")
public class Day01IT {

    @Test
    @DisplayName("HealthServlet trả về HTTP 200 với JSON UTF-8 cố định và không lộ cấu hình/bí mật")
    public void testHealthServletLiveness() throws Exception {
        HealthServlet servlet = new HealthServlet();

        final Map<String, Object> responseHeaders = new HashMap<>();
        final StringWriter stringWriter = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(stringWriter);
        final int[] statusCode = new int[]{200};

        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    if ("getMethod".equals(method.getName())) return "GET";
                    if ("getRequestURI".equals(method.getName())) return "/health/live";
                    return null;
                }
        );

        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> {
                    if ("setStatus".equals(method.getName()) && args != null && args.length > 0) {
                        statusCode[0] = (int) args[0];
                        return null;
                    }
                    if ("setContentType".equals(method.getName()) && args != null && args.length > 0) {
                        responseHeaders.put("Content-Type", args[0]);
                        return null;
                    }
                    if ("setCharacterEncoding".equals(method.getName()) && args != null && args.length > 0) {
                        responseHeaders.put("Character-Encoding", args[0]);
                        return null;
                    }
                    if ("getWriter".equals(method.getName())) {
                        return printWriter;
                    }
                    return null;
                }
        );

        java.lang.reflect.Method doGetMethod = HealthServlet.class.getDeclaredMethod("doGet", HttpServletRequest.class, HttpServletResponse.class);
        doGetMethod.setAccessible(true);
        doGetMethod.invoke(servlet, request, response);

        printWriter.flush();
        String output = stringWriter.toString().trim();

        assertEquals(200, statusCode[0], "HTTP status code phải là 200 OK");
        assertEquals("{\"data\":{\"status\":\"UP\"}}", output, "Response body phải khớp đúng envelope hợp đồng");
        assertFalse(output.contains("password") || output.contains("secret") || output.contains("sql"), "Không được để lộ thông tin cấu hình");
    }
}
