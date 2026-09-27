package vn.ticketscenter.controller.identity;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import vn.ticketscenter.config.PersistenceListener;
import vn.ticketscenter.config.PersistenceRegistry;
import vn.ticketscenter.controller.HttpResponses;
import vn.ticketscenter.filter.AuthenticationFilter;
import vn.ticketscenter.service.identity.*;
import vn.ticketscenter.util.JsonObjectParser;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;

@WebServlet(name = "AuthServlet", urlPatterns = {
        "/api/auth/csrf", "/api/auth/register", "/api/auth/login", "/api/auth/logout", "/api/me"
})
public final class AuthServlet extends HttpServlet {

    private final CsrfService csrf = new CsrfService();
    private final SessionService sessions = new SessionService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (request.getRequestURI().endsWith("/auth/csrf")) {
            HttpResponses.data(response, "{\"token\":" + HttpResponses.jsonString(csrf.token(request.getSession(true))) + "}");
            return;
        }
        Object account = request.getAttribute(AuthenticationFilter.ACCOUNT_ATTRIBUTE);
        if (account instanceof AccountService.AuthenticatedAccount current) {
            HttpResponses.data(response, "{\"id\":" + HttpResponses.jsonString(current.id().toString())
                    + ",\"email\":" + HttpResponses.jsonString(current.email())
                    + ",\"admin\":" + current.admin() + "}");
            return;
        }
        HttpResponses.error(response, HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND", "Resource not found");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String path = request.getRequestURI();
        if (path.endsWith("/auth/logout")) {
            sessions.logout(request);
            HttpResponses.data(response, "{\"loggedOut\":true}");
            return;
        }
        AccountService accounts = accounts(request, response);
        if (accounts == null) return;
        try {
            Map<String, String> body = credentials(request);
            if (path.endsWith("/auth/register")) {
                accounts.register(body.get("email"), body.get("password"));
                HttpResponses.data(response, "{\"accepted\":true}");
                return;
            }
            if (path.endsWith("/auth/login")) {
                var account = accounts.authenticate(body.get("email"), body.get("password")).orElse(null);
                if (account == null) {
                    HttpResponses.error(response, HttpServletResponse.SC_UNAUTHORIZED,
                            "INVALID_CREDENTIALS", "Email or password is invalid");
                    return;
                }
                sessions.login(request, account.id(), account.authVersion());
                HttpResponses.data(response, "{\"authenticated\":true}");
                return;
            }
            HttpResponses.error(response, HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND", "Resource not found");
        } catch (IllegalArgumentException exception) {
            HttpResponses.error(response, HttpServletResponse.SC_BAD_REQUEST, "VALIDATION_FAILED", "Request is invalid");
        }
    }

    private AccountService accounts(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object configured = request.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
        if (!(configured instanceof PersistenceRegistry registry)) {
            HttpResponses.error(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "DEPENDENCY_UNAVAILABLE", "Authentication is temporarily unavailable");
            return null;
        }
        return new AccountService(registry.transactionManager(), new PasswordHasher(), Clock.systemUTC());
    }

    private Map<String, String> credentials(HttpServletRequest request) throws IOException {
        if (request.getContentType().startsWith("application/x-www-form-urlencoded")) {
            return Map.of("email", value(request.getParameter("email")), "password", value(request.getParameter("password")));
        }
        return JsonObjectParser.parse(request.getReader());
    }

    private String value(String value) {
        if (value == null) throw new IllegalArgumentException("missing field");
        return value;
    }
}
