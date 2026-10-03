package vn.ticketscenter.event.service;

import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.event.dto.EventDtos.CreateEventCommand;
import vn.ticketscenter.event.dto.EventDtos.EventPage;
import vn.ticketscenter.event.dto.EventDtos.EventSearch;
import vn.ticketscenter.event.dto.EventDtos.EventView;
import vn.ticketscenter.event.dto.EventDtos.SeatSpec;
import vn.ticketscenter.event.dto.EventDtos.ZoneCommand;
import vn.ticketscenter.event.dto.EventDtos.ZoneView;
import vn.ticketscenter.event.integration.storage.ImageStorage;
import vn.ticketscenter.event.repository.EventRepository;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;
import vn.ticketscenter.identity.service.AuthorizationService;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

public final class EventService {
    private static final int MAX_ROWS = 100;
    private static final int MAX_SEATS_PER_ROW = 500;
    private static final int MAX_SEATS_PER_ZONE = 10_000;

    private final TransactionManager transactions;
    private final EventRepository events;
    private final PublicEventCache publicEventCache = new PublicEventCache();

    public EventService(TransactionManager transactions, EventRepository events) {
        this.transactions = Objects.requireNonNull(transactions);
        this.events = Objects.requireNonNull(events);
    }

    public UUID createDraft(AuthenticatedAccount account, UUID organizationId, CreateEventCommand command) {
        requireAccount(account);
        UUID organization = requireId(organizationId);
        CreateEventCommand valid = validateEvent(command);
        return transactions.execute(DatabasePrincipal.MANAGER,
                entityManager -> events.createDraft(entityManager, account.id(), organization, valid));
    }

    public UUID addZone(AuthenticatedAccount account, UUID eventId, ZoneCommand command) {
        requireAccount(account);
        UUID id = requireId(eventId);
        ZoneCommand valid = validateZone(command);
        List<SeatSpec> seats = seats(valid);
        return transactions.execute(DatabasePrincipal.MANAGER,
                entityManager -> events.addZone(entityManager, account.id(), id, valid, seats));
    }

    public void updateDraft(AuthenticatedAccount account, UUID eventId, CreateEventCommand command) {
        requireAccount(account);
        CreateEventCommand valid = validateEvent(command);
        transactions.execute(DatabasePrincipal.MANAGER, entityManager -> {
            events.updateDraft(entityManager, account.id(), requireId(eventId), valid);
            return null;
        });
    }

    public void deleteDraft(AuthenticatedAccount account, UUID eventId) {
        requireAccount(account);
        transactions.execute(DatabasePrincipal.MANAGER, entityManager -> {
            events.deleteDraft(entityManager, account.id(), requireId(eventId));
            return null;
        });
    }

    public void updateZone(AuthenticatedAccount account, UUID zoneId, ZoneCommand command) {
        requireAccount(account);
        ZoneCommand valid = validateZone(command);
        transactions.execute(DatabasePrincipal.MANAGER, entityManager -> {
            events.replaceZone(entityManager, account.id(), requireId(zoneId), valid, seats(valid));
            return null;
        });
    }

    public void deleteZone(AuthenticatedAccount account, UUID zoneId) {
        requireAccount(account);
        transactions.execute(DatabasePrincipal.MANAGER, entityManager -> {
            events.deleteZone(entityManager, account.id(), requireId(zoneId));
            return null;
        });
    }

    public void submit(AuthenticatedAccount account, UUID eventId) {
        requireAccount(account);
        transactions.execute(DatabasePrincipal.MANAGER, entityManager -> {
            events.submit(entityManager, account.id(), requireId(eventId));
            return null;
        });
    }

    public void publish(AuthenticatedAccount account, UUID eventId, UUID commissionRuleId) {
        AuthorizationService.requireAdmin(account);
        UUID id = requireId(eventId);
        transactions.execute(DatabasePrincipal.ADMIN, entityManager -> {
            events.publish(entityManager, id, account.id(), requireId(commissionRuleId));
            return null;
        });
        publicEventCache.invalidate(id);
    }

    public void reject(AuthenticatedAccount account, UUID eventId, String reason) {
        AuthorizationService.requireAdmin(account);
        String validReason = requiredText(reason, 1000, "rejection reason");
        transactions.execute(DatabasePrincipal.ADMIN, entityManager -> {
            events.reject(entityManager, requireId(eventId), account.id(), validReason);
            return null;
        });
    }

    public String uploadCover(AuthenticatedAccount account, UUID eventId, InputStream content,
                              String submittedName, ImageStorage storage) throws IOException {
        requireAccount(account);
        UUID id = requireId(eventId);
        transactions.execute(DatabasePrincipal.MANAGER, entityManager -> {
            events.requireEditable(entityManager, account.id(), id);
            return null;
        });
        String newUrl = storage.store(content, submittedName);
        String oldUrl;
        try {
            oldUrl = transactions.execute(DatabasePrincipal.MANAGER,
                    entityManager -> events.replaceCover(entityManager, account.id(), id, newUrl));
        } catch (RuntimeException exception) {
            try { storage.delete(newUrl); } catch (IOException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
            throw exception;
        }
        if (oldUrl != null && !oldUrl.equals(newUrl)) {
            try {
                storage.delete(oldUrl);
            } catch (IOException ignored) {
                // ponytail: best-effort cleanup; add a storage-cleanup outbox worker when orphan volume matters.
            }
        }
        return newUrl;
    }

    public EventPage search(EventSearch search) {
        EventSearch valid = validateSearch(search);
        return transactions.execute(DatabasePrincipal.BUYER, entityManager -> events.search(entityManager, valid));
    }

    public EventPage getOrganizationEvents(AuthenticatedAccount account, UUID organizationId, int page, int pageSize) {
        requireAccount(account);
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("invalid pagination");
        return transactions.execute(DatabasePrincipal.MANAGER, entityManager ->
                events.findForOrganization(entityManager, account.id(), requireId(organizationId), page, pageSize));
    }

    public EventView getPublic(UUID eventId) {
        EventView event = transactions.execute(DatabasePrincipal.BUYER,
                entityManager -> events.findPublic(entityManager, requireId(eventId)));
        if (event == null) throw new NoSuchElementException("event not found");
        return event;
    }

    public List<ZoneView> getPublicZones(UUID eventId) {
        return transactions.execute(DatabasePrincipal.BUYER,
                entityManager -> events.findPublicZones(entityManager, requireId(eventId)));
    }

    public List<Object[]> getCategories() {
        return transactions.execute(DatabasePrincipal.BUYER, events::findCategories);
    }

    private CreateEventCommand validateEvent(CreateEventCommand command) {
        if (command == null || command.categoryId() == null || command.saleStart() == null
                || command.saleEnd() == null || command.startTime() == null || command.endTime() == null) {
            throw new IllegalArgumentException("event fields are required");
        }
        if (!command.saleStart().isBefore(command.saleEnd()) || command.saleEnd().isAfter(command.startTime())
                || !command.startTime().isBefore(command.endTime())) {
            throw new IllegalArgumentException("saleStart < saleEnd <= startTime < endTime");
        }
        return new CreateEventCommand(command.categoryId(), requiredText(command.title(), 250, "title"),
                optionalText(command.description(), 10_000, "description"),
                requiredText(command.venueName(), 250, "venue name"),
                requiredText(command.venueAddress(), 500, "venue address"),
                command.saleStart(), command.saleEnd(), command.startTime(), command.endTime());
    }

    private ZoneCommand validateZone(ZoneCommand command) {
        if (command == null || command.price() == null || command.price().signum() < 0) {
            throw new IllegalArgumentException("zone and non-negative price are required");
        }
        String type = requiredText(command.type(), 20, "zone type").toUpperCase(java.util.Locale.ROOT);
        if ("STANDING".equals(type)) {
            if (command.capacity() == null || command.capacity() <= 0 || command.rows() != null || command.seatsPerRow() != null) {
                throw new IllegalArgumentException("standing zone requires positive capacity only");
            }
        } else if ("SEATED".equals(type)) {
            if (command.capacity() != null || command.rows() == null || command.seatsPerRow() == null
                    || command.rows() <= 0 || command.seatsPerRow() <= 0 || command.rows() > MAX_ROWS
                    || command.seatsPerRow() > MAX_SEATS_PER_ROW
                    || (long) command.rows() * command.seatsPerRow() > MAX_SEATS_PER_ZONE) {
                throw new IllegalArgumentException("seated zone requires valid rows and seatsPerRow");
            }
        } else {
            throw new IllegalArgumentException("zone type must be SEATED or STANDING");
        }
        if (command.price().stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("price must be an integer VND amount");
        }
        return new ZoneCommand(requiredText(command.name(), 120, "zone name"), type,
                command.price().setScale(0), command.capacity(), command.rows(), command.seatsPerRow());
    }

    private List<SeatSpec> seats(ZoneCommand command) {
        if (!"SEATED".equals(command.type())) return List.of();
        List<SeatSpec> seats = new ArrayList<>(command.rows() * command.seatsPerRow());
        for (int row = 1; row <= command.rows(); row++) {
            String rowName = rowName(row);
            for (int number = 1; number <= command.seatsPerRow(); number++) {
                seats.add(new SeatSpec(rowName, number, rowName + number));
            }
        }
        return List.copyOf(seats);
    }

    private String rowName(int number) {
        StringBuilder value = new StringBuilder();
        for (int current = number; current > 0; current = (current - 1) / 26) {
            value.append((char) ('A' + (current - 1) % 26));
        }
        return value.reverse().toString();
    }

    private EventSearch validateSearch(EventSearch search) {
        if (search == null || search.page() < 1 || search.pageSize() < 1 || search.pageSize() > 100) {
            throw new IllegalArgumentException("invalid pagination");
        }
        String sort = search.sort() == null || search.sort().isBlank() ? "start" : search.sort();
        if (!"start".equals(sort) && !"minPrice".equals(sort)) throw new IllegalArgumentException("invalid sort");
        if (search.from() != null && search.to() != null && !search.from().isBefore(search.to())) {
            throw new IllegalArgumentException("from must be before to");
        }
        return new EventSearch(optionalText(search.query(), 250, "query"), search.categoryId(),
                search.from(), search.to(), sort, search.page(), search.pageSize());
    }

    private void requireAccount(AuthenticatedAccount account) {
        if (account == null || account.id() == null) throw new SecurityException("authenticated user required");
    }

    private UUID requireId(UUID id) {
        if (id == null) throw new IllegalArgumentException("id is required");
        return id;
    }

    private String requiredText(String value, int maxLength, String field) {
        String result = optionalText(value, maxLength, field);
        if (result == null) throw new IllegalArgumentException(field + " is required");
        return result;
    }

    private String optionalText(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > maxLength) throw new IllegalArgumentException(field + " is too long");
        return result;
    }
}
