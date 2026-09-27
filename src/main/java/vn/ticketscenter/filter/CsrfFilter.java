package vn.ticketscenter.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import vn.ticketscenter.controller.HttpResponses;
import vn.ticketscenter.service.identity.CsrfService;

import java.io.IOException;
import java.util.Set;

@WebFilter(urlPatterns = "/api/*")
public final class CsrfFilter implements Filter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private final CsrfService csrf = new CsrfService();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        if (SAFE_METHODS.contains(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        HttpSession session = httpRequest.getSession(false);
        if (session == null || !csrf.matches(session, httpRequest.getHeader("X-CSRF-Token"))) {
            HttpResponses.error(httpResponse, HttpServletResponse.SC_FORBIDDEN, "CSRF_INVALID", "CSRF token is invalid");
            return;
        }
        chain.doFilter(request, response);
    }
}
