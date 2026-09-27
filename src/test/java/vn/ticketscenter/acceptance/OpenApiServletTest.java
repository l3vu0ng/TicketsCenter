package vn.ticketscenter.acceptance;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.controller.OpenApiServlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiServletTest {

    @Test
    void redirectsSwaggerPathToSwaggerIndexHtml() throws Exception {
        OpenApiServlet servlet = new OpenApiServlet();
        String[] redirectedLocation = new String[1];

        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMethod" -> "GET";
                    case "getRequestURI" -> "/tc/swagger";
                    case "getContextPath" -> "/tc";
                    default -> null;
                });

        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "sendRedirect" -> {
                        redirectedLocation[0] = (String) args[0];
                        yield null;
                    }
                    default -> null;
                });

        servlet.service(request, response);
        assertEquals("/tc/swagger/index.html", redirectedLocation[0]);
    }

    @Test
    void servesValidOpenApiJsonSpecification() throws Exception {
        OpenApiServlet servlet = new OpenApiServlet();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int[] statusCode = new int[]{200};
        String[] contentType = new String[1];

        ServletOutputStream servletOutputStream = new ServletOutputStream() {
            @Override public boolean isReady() { return true; }
            @Override public void setWriteListener(WriteListener writeListener) {}
            @Override public void write(int b) throws IOException { output.write(b); }
            @Override public void write(byte[] b, int off, int len) throws IOException { output.write(b, off, len); }
        };

        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMethod" -> "GET";
                    case "getRequestURI" -> "/tc/api/openapi.json";
                    case "getContextPath" -> "/tc";
                    default -> null;
                });

        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setStatus" -> { statusCode[0] = (int) args[0]; yield null; }
                    case "setContentType" -> { contentType[0] = (String) args[0]; yield null; }
                    case "setHeader" -> null;
                    case "getOutputStream" -> servletOutputStream;
                    default -> null;
                });

        servlet.service(request, response);

        assertEquals(200, statusCode[0]);
        assertTrue(contentType[0].contains("application/json"));
        String jsonContent = output.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(jsonContent.contains("\"openapi\": \"3.0.3\""));
        assertTrue(jsonContent.contains("\"/api/auth/register\""));
        assertTrue(jsonContent.contains("\"/api/auth/otp/send\""));
        assertTrue(jsonContent.contains("\"/api/auth/password/reset\""));
    }
}
