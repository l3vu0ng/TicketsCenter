package vn.ticketscenter.identity.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.dto.OrganizationDtos.ProfileView;
import vn.ticketscenter.identity.filter.AuthenticationFilter;
import vn.ticketscenter.identity.repository.OrganizationRepository;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;
import vn.ticketscenter.identity.service.OrganizationService;

import java.io.IOException;

@WebServlet(name = "ProfileServlet", urlPatterns = "/api/me/profile")
public final class ProfileServlet extends HttpServlet {
    private final OrganizationService organizationService;

    public ProfileServlet() { this.organizationService = null; }
    public ProfileServlet(OrganizationService organizationService) { this.organizationService = organizationService; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object value = request.getAttribute(AuthenticationFilter.ACCOUNT_ATTRIBUTE);
        if (!(value instanceof AuthenticatedAccount account)) {
            HttpResponses.error(response, 401, "UNAUTHORIZED", "Yêu cầu đăng nhập để truy cập");
            return;
        }
        ProfileView profile = service(request).getProfile(account);
        HttpResponses.data(response, json(profile));
    }

    private OrganizationService service(HttpServletRequest request) {
        if (organizationService != null) return organizationService;
        Object configured = request.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
        if (configured instanceof PersistenceRegistry registry) {
            return new OrganizationService(registry.transactionManager(), new OrganizationRepository());
        }
        throw new IllegalStateException("profile service is unavailable");
    }

    static String json(ProfileView value) {
        return "{\"id\":" + OrganizationServlet.json(value.id()) + ",\"email\":" + OrganizationServlet.json(value.email())
                + ",\"fullName\":" + OrganizationServlet.json(value.fullName()) + ",\"phone\":" + OrganizationServlet.json(value.phone())
                + ",\"emailVerified\":" + value.emailVerified() + ",\"platformRoles\":["
                + value.platformRoles().stream().map(OrganizationServlet::json).reduce((a, b) -> a + "," + b).orElse("")
                + "],\"memberships\":" + OrganizationServlet.membershipsJson(value.memberships()) + "}";
    }
}
