package vn.ticketscenter.ticketing;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;
import vn.ticketscenter.ticketing.dto.TicketingDtos.CreateHoldRequest;
import vn.ticketscenter.ticketing.dto.TicketingDtos.HoldItemRequest;
import vn.ticketscenter.ticketing.dto.TicketingDtos.HoldResponseDto;
import vn.ticketscenter.ticketing.repository.HoldRepository;
import vn.ticketscenter.ticketing.service.HoldService;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HoldServiceTest {
    private static final UUID ACTOR = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID EVENT = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID ZONE = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final AuthenticatedAccount ACCOUNT = new AuthenticatedAccount(ACTOR, 0, "buyer@example.test", false);

    @Test
    void acceptsOneToEightTicketsAndUsesAuthenticatedBuyer() {
        FakeRepository repository = new FakeRepository();
        HoldService service = new HoldService(transactions(), repository);

        HoldResponseDto result = service.create(ACCOUNT, new CreateHoldRequest(EVENT,
                List.of(new HoldItemRequest(ZONE, null, 2))));

        assertEquals(EVENT, repository.eventId);
        assertEquals(ACTOR, repository.actorId);
        assertEquals(2, repository.items.getFirst().quantity());
        assertEquals("ACTIVE", result.status().name());
    }

    @Test
    void rejectsInvalidQuantityBeforeDatabase() {
        HoldService service = new HoldService(transactions(), new FakeRepository());
        assertThrows(IllegalArgumentException.class, () -> service.create(ACCOUNT,
                new CreateHoldRequest(EVENT, List.of(new HoldItemRequest(ZONE, null, 9)))));
    }

    @Test
    void cancelRequiresAuthenticatedOwner() {
        HoldService service = new HoldService(transactions(), new FakeRepository());
        assertThrows(SecurityException.class, () -> service.cancel(null, UUID.randomUUID()));
    }

    private static TransactionManager transactions() {
        EntityTransaction transaction = (EntityTransaction) Proxy.newProxyInstance(EntityTransaction.class.getClassLoader(),
                new Class<?>[]{EntityTransaction.class}, (proxy, method, args) -> "isActive".equals(method.getName()));
        EntityManager entityManager = (EntityManager) Proxy.newProxyInstance(EntityManager.class.getClassLoader(),
                new Class<?>[]{EntityManager.class}, (proxy, method, args) -> "getTransaction".equals(method.getName()) ? transaction : null);
        EntityManagerFactory factory = (EntityManagerFactory) Proxy.newProxyInstance(EntityManagerFactory.class.getClassLoader(),
                new Class<?>[]{EntityManagerFactory.class}, (proxy, method, args) -> "createEntityManager".equals(method.getName()) ? entityManager : null);
        return new TransactionManager(Map.of(DatabasePrincipal.BUYER, factory));
    }

    private static final class FakeRepository extends HoldRepository {
        private UUID actorId;
        private UUID eventId;
        private List<HoldItemRequest> items;

        @Override
        public HoldResponseDto create(EntityManager entityManager, UUID actorId, UUID eventId, List<HoldItemRequest> items) {
            this.actorId = actorId;
            this.eventId = eventId;
            this.items = items;
            return new HoldResponseDto(UUID.randomUUID(), eventId, vn.ticketscenter.ticketing.model.TicketingEnums.TicketHoldStatus.ACTIVE,
                    Instant.parse("2030-01-01T00:10:00Z"), 200L);
        }
    }
}
