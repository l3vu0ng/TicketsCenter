package vn.ticketscenter.config.web;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;

import java.io.IOException;
import java.util.Set;

@WebFilter(urlPatterns = "/api/*")
public final class RequestValidationFilter implements Filter {

    private static final long MAX_BODY_BYTES = 64 * 1024;
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setHeader("Cache-Control", "no-store");
        if (httpRequest.getContentLengthLong() > MAX_BODY_BYTES) {
            HttpResponses.error(httpResponse, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "PAYLOAD_TOO_LARGE", "Request payload is too large");
            return;
        }
        if (BODY_METHODS.contains(httpRequest.getMethod())) {
            String contentType = httpRequest.getContentType();
            if (contentType == null || !(contentType.startsWith("application/json")
                    || contentType.startsWith("application/x-www-form-urlencoded"))) {
                HttpResponses.error(httpResponse, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                        "CONTENT_TYPE_INVALID", "Unsupported content type");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
