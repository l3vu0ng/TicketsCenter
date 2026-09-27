package vn.ticketscenter.admin;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.admin.dto.AdminDtos.AdminDashboardSummary;
import vn.ticketscenter.admin.dto.AdminDtos.OrganizationApprovalRequest;
import vn.ticketscenter.admin.service.AdminService;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AdminService Unit Tests")
class AdminServiceTest {

    private static TransactionManager createTestTransactionManager() {
        EntityTransaction transaction = (EntityTransaction) Proxy.newProxyInstance(
                EntityTransaction.class.getClassLoader(), new Class<?>[]{EntityTransaction.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isActive" -> false;
                    default -> null;
                });
        EntityManager entityManager = (EntityManager) Proxy.newProxyInstance(
                EntityManager.class.getClassLoader(), new Class<?>[]{EntityManager.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getTransaction" -> transaction;
                    default -> null;
                });
        EntityManagerFactory factory = (EntityManagerFactory) Proxy.newProxyInstance(
                EntityManagerFactory.class.getClassLoader(), new Class<?>[]{EntityManagerFactory.class},
                (proxy, method, args) -> "createEntityManager".equals(method.getName()) ? entityManager : null);
        return new TransactionManager(Map.of(DatabasePrincipal.ADMIN, factory));
    }

    @Test
    @DisplayName("Non-admin user cannot access admin dashboard")
    void testNonAdminBlockedFromDashboard() {
        AdminService service = new AdminService(createTestTransactionManager());
        AuthenticatedAccount customer = new AuthenticatedAccount(
                UUID.randomUUID(), 1, "customer@test.com", false);

        assertThrows(SecurityException.class, () -> service.getDashboardSummary(customer));
    }

    @Test
    @DisplayName("Non-admin user cannot process organization requests")
    void testNonAdminBlockedFromApproval() {
        AdminService service = new AdminService(createTestTransactionManager());
        AuthenticatedAccount customer = new AuthenticatedAccount(
                UUID.randomUUID(), 1, "customer@test.com", false);

        OrganizationApprovalRequest req = new OrganizationApprovalRequest(UUID.randomUUID(), true, null);
        assertThrows(SecurityException.class, () -> service.processOrganizationRequest(customer, req));
    }

    @Test
    @DisplayName("Unauthenticated request is rejected")
    void testUnauthenticatedBlocked() {
        AdminService service = new AdminService(createTestTransactionManager());
        assertThrows(SecurityException.class, () -> service.getDashboardSummary(null));
    }

    @Test
    @DisplayName("Admin user with null approval request returns false")
    void testNullApprovalRequest() {
        AdminService service = new AdminService(createTestTransactionManager());
        AuthenticatedAccount admin = new AuthenticatedAccount(
                UUID.randomUUID(), 1, "admin@test.com", true);

        assertFalse(service.processOrganizationRequest(admin, null));
    }
}
