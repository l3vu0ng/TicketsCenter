package vn.ticketscenter.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import vn.ticketscenter.config.PersistenceListener;
import vn.ticketscenter.config.PersistenceRegistry;
import vn.ticketscenter.controller.HttpResponses;
import vn.ticketscenter.service.identity.AccountService;
import vn.ticketscenter.service.identity.PasswordHasher;
import vn.ticketscenter.service.identity.SessionService;

import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;

@WebFilter(urlPatterns = "/api/*")
public final class AuthenticationFilter implements Filter {

    public static final String ACCOUNT_ATTRIBUTE = "ticketscenter.account";
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/csrf", "/api/auth/register", "/api/auth/login");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());
        if (PUBLIC_PATHS.contains(path) || ("GET".equals(httpRequest.getMethod()) && path.startsWith("/api/events"))) {
            chain.doFilter(request, response);
            return;
        }
        HttpSession session = httpRequest.getSession(false);
        if (session == null || !(session.getAttribute(SessionService.USER_ID) instanceof UUID userId)
                || !(session.getAttribute(SessionService.AUTH_VERSION) instanceof Integer sessionVersion)) {
            unauthorized(httpResponse);
            return;
        }
        Object configured = httpRequest.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
        if (!(configured instanceof PersistenceRegistry registry)) {
            HttpResponses.error(httpResponse, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "DEPENDENCY_UNAVAILABLE", "Authentication is temporarily unavailable");
            return;
        }
        AccountService.AuthenticatedAccount account = new AccountService(
                registry.transactionManager(), new PasswordHasher(), Clock.systemUTC()).current(userId).orElse(null);
        if (account == null || account.authVersion() != sessionVersion) {
            session.invalidate();
            unauthorized(httpResponse);
            return;
        }
        httpRequest.setAttribute(ACCOUNT_ATTRIBUTE, account);
        chain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        HttpResponses.error(response, HttpServletResponse.SC_UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED", "Authentication is required");
    }
}
