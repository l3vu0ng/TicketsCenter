package vn.ticketscenter.identity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.dto.OrganizationDtos.MemberCommand;
import vn.ticketscenter.identity.dto.OrganizationDtos.OrganizationRequestCommand;
import vn.ticketscenter.identity.repository.OrganizationRepository;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;
import vn.ticketscenter.identity.service.OrganizationService;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrganizationServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ORGANIZATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID REQUEST_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final AuthenticatedAccount USER = new AuthenticatedAccount(ACTOR_ID, 0, "user@example.test", false);
    private static final AuthenticatedAccount ADMIN = new AuthenticatedAccount(ACTOR_ID, 0, "admin@example.test", true);

    @Test
    void createRequestUsesAuthenticatedApplicantAndNormalizesInput() {
        FakeRepository repository = new FakeRepository();
        OrganizationService service = service(repository, "{\"ratePercent\":5,\"fixedFee\":0,\"effectiveFrom\":\"2026-01-01\",\"effectiveTo\":\"2027-01-01\"}");

        UUID created = service.createRequest(USER,
                new OrganizationRequestCommand("  Nhà tổ chức  ", " Contact@Example.Test ", " 0901234567 ", " mô tả "));

        assertEquals(REQUEST_ID, created);
        assertEquals(ACTOR_ID, repository.applicantId);
        assertEquals("Nhà tổ chức", repository.request.name());
        assertEquals("contact@example.test", repository.request.contactEmail());
    }

    @Test
    void rejectRequiresAdminAndNonBlankReason() {
        OrganizationService service = service(new FakeRepository(), "{}");

        assertThrows(SecurityException.class, () -> service.rejectRequest(USER, REQUEST_ID, "không hợp lệ"));
        assertThrows(IllegalArgumentException.class, () -> service.rejectRequest(ADMIN, REQUEST_ID, "  "));
    }

    @Test
    void approvalUsesServerPolicyAndIsIdempotentAtRepositoryBoundary() {
        FakeRepository repository = new FakeRepository();
        String policy = "{\"ratePercent\":5,\"fixedFee\":1000,\"effectiveFrom\":\"2026-01-01\",\"effectiveTo\":\"2027-01-01\"}";
        OrganizationService service = service(repository, policy);

        UUID approved = service.approveRequest(ADMIN, REQUEST_ID);

        assertEquals(ORGANIZATION_ID, approved);
        assertEquals(policy, repository.policy);
        assertEquals(ACTOR_ID, repository.reviewerId);
    }

    @Test
    void memberMutationRequiresManagerInSameOrganization() {
        FakeRepository repository = new FakeRepository();
        repository.manager = false;
        OrganizationService service = service(repository, "{}");

        assertThrows(SecurityException.class, () -> service.addOrReactivateMember(USER, ORGANIZATION_ID,
                new MemberCommand("staff@example.test", "CHECK_IN_STAFF")));
        assertFalse(repository.memberWritten);
    }

    @Test
    void memberRoleOnlyAcceptsDomainRoles() {
        FakeRepository repository = new FakeRepository();
        OrganizationService service = service(repository, "{}");

        assertThrows(IllegalArgumentException.class, () -> service.addOrReactivateMember(USER, ORGANIZATION_ID,
                new MemberCommand("staff@example.test", "ADMIN")));
    }

    private static OrganizationService service(FakeRepository repository, String policy) {
        return new OrganizationService(transactions(), repository, () -> policy);
    }

    private static TransactionManager transactions() {
        EntityTransaction transaction = (EntityTransaction) Proxy.newProxyInstance(
                EntityTransaction.class.getClassLoader(), new Class<?>[]{EntityTransaction.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isActive" -> true;
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
        return new TransactionManager(Map.of(
                DatabasePrincipal.BUYER, factory,
                DatabasePrincipal.MANAGER, factory,
                DatabasePrincipal.ADMIN, factory));
    }

    private static final class FakeRepository extends OrganizationRepository {
        private UUID applicantId;
        private UUID reviewerId;
        private OrganizationRequestCommand request;
        private String policy;
        private boolean manager = true;
        private boolean memberWritten;

        @Override
        public UUID createRequest(EntityManager entityManager, UUID applicantId, OrganizationRequestCommand request) {
            this.applicantId = applicantId;
            this.request = request;
            return REQUEST_ID;
        }

        @Override
        public UUID approveRequest(EntityManager entityManager, UUID requestId, UUID reviewerId, String policy) {
            this.reviewerId = reviewerId;
            this.policy = policy;
            return ORGANIZATION_ID;
        }

        @Override
        public boolean isActiveManager(EntityManager entityManager, UUID actorId, UUID organizationId) {
            return manager;
        }

        @Override
        public void upsertMember(
                EntityManager entityManager, UUID actorId, UUID organizationId, MemberCommand command) {
            if (!manager) throw new SecurityException("active manager membership required");
            memberWritten = true;
        }
    }
}
