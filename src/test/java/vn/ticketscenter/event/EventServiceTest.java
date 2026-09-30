package vn.ticketscenter.event;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.event.dto.EventDtos.CreateEventCommand;
import vn.ticketscenter.event.dto.EventDtos.SeatSpec;
import vn.ticketscenter.event.dto.EventDtos.ZoneCommand;
import vn.ticketscenter.event.repository.EventRepository;
import vn.ticketscenter.event.service.EventService;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventServiceTest {
    private static final UUID ACTOR_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ORGANIZATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID EVENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID CATEGORY_ID = UUID.fromString("10000000-0000-0000-0000-000000000004");
    private static final AuthenticatedAccount MANAGER = new AuthenticatedAccount(ACTOR_ID, 0, "manager@example.test", false);

    @Test
    void createsDraftOnlyWhenScheduleIsValid() {
        FakeRepository repository = new FakeRepository();
        EventService service = new EventService(transactions(), repository);

        UUID id = service.createDraft(MANAGER, ORGANIZATION_ID, validEvent());

        assertEquals(EVENT_ID, id);
        assertEquals(ACTOR_ID, repository.actorId);
        assertEquals("Đêm nhạc", repository.event.title());
        CreateEventCommand invalid = new CreateEventCommand(CATEGORY_ID, "Sai lịch", null, "Nhà hát", "Hà Nội",
                Instant.parse("2026-10-01T10:00:00Z"), Instant.parse("2026-10-01T09:00:00Z"),
                Instant.parse("2026-10-02T10:00:00Z"), Instant.parse("2026-10-02T12:00:00Z"));
        assertThrows(IllegalArgumentException.class,
                () -> service.createDraft(MANAGER, ORGANIZATION_ID, invalid));
    }

    @Test
    void seatedZoneGeneratesSpreadsheetStyleRowsAndStandingZoneDoesNotGenerateSeats() {
        FakeRepository repository = new FakeRepository();
        EventService service = new EventService(transactions(), repository);

        service.addZone(MANAGER, EVENT_ID,
                new ZoneCommand("Khán đài", "SEATED", BigDecimal.ZERO, null, 27, 2));

        assertEquals(54, repository.seats.size());
        assertEquals(new SeatSpec("A", 1, "A1"), repository.seats.getFirst());
        assertEquals(new SeatSpec("AA", 2, "AA2"), repository.seats.getLast());

        service.addZone(MANAGER, EVENT_ID,
                new ZoneCommand("Khu đứng", "STANDING", new BigDecimal("200000"), 100, null, null));
        assertTrue(repository.seats.isEmpty());
    }

    @Test
    void rejectsInvalidZoneShapesAndBlankRejectionReason() {
        EventService service = new EventService(transactions(), new FakeRepository());

        assertThrows(IllegalArgumentException.class, () -> service.addZone(MANAGER, EVENT_ID,
                new ZoneCommand("Sai", "STANDING", BigDecimal.ZERO, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.addZone(MANAGER, EVENT_ID,
                new ZoneCommand("Sai", "SEATED", BigDecimal.ZERO, null, 0, 2)));
        assertThrows(IllegalArgumentException.class, () -> service.addZone(MANAGER, EVENT_ID,
                new ZoneCommand("Sai", "STANDING", new BigDecimal("1.5"), 10, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.reject(
                new AuthenticatedAccount(ACTOR_ID, 0, "admin@example.test", true), EVENT_ID, " "));
        assertThrows(IllegalArgumentException.class, () -> service.search(
                new vn.ticketscenter.event.dto.EventDtos.EventSearch(null, null, null, null, "title; DROP TABLE", 1, 20)));
    }

    private static CreateEventCommand validEvent() {
        return new CreateEventCommand(CATEGORY_ID, "  Đêm nhạc  ", " Mô tả ", " Nhà hát ", " Hà Nội ",
                Instant.parse("2026-10-01T08:00:00Z"), Instant.parse("2026-10-02T08:00:00Z"),
                Instant.parse("2026-10-02T08:00:00Z"), Instant.parse("2026-10-02T12:00:00Z"));
    }

    private static TransactionManager transactions() {
        EntityTransaction transaction = (EntityTransaction) Proxy.newProxyInstance(
                EntityTransaction.class.getClassLoader(), new Class<?>[]{EntityTransaction.class},
                (proxy, method, args) -> "isActive".equals(method.getName()));
        EntityManager entityManager = (EntityManager) Proxy.newProxyInstance(
                EntityManager.class.getClassLoader(), new Class<?>[]{EntityManager.class},
                (proxy, method, args) -> "getTransaction".equals(method.getName()) ? transaction : null);
        EntityManagerFactory factory = (EntityManagerFactory) Proxy.newProxyInstance(
                EntityManagerFactory.class.getClassLoader(), new Class<?>[]{EntityManagerFactory.class},
                (proxy, method, args) -> "createEntityManager".equals(method.getName()) ? entityManager : null);
        return new TransactionManager(Map.of(
                DatabasePrincipal.MANAGER, factory,
                DatabasePrincipal.ADMIN, factory,
                DatabasePrincipal.BUYER, factory));
    }

    private static final class FakeRepository extends EventRepository {
        private UUID actorId;
        private CreateEventCommand event;
        private List<SeatSpec> seats = List.of();

        @Override
        public UUID createDraft(EntityManager entityManager, UUID actorId, UUID organizationId, CreateEventCommand command) {
            this.actorId = actorId;
            this.event = command;
            return EVENT_ID;
        }

        @Override
        public UUID addZone(EntityManager entityManager, UUID actorId, UUID eventId,
                            ZoneCommand command, List<SeatSpec> seats) {
            this.seats = seats;
            return UUID.randomUUID();
        }
    }
}
