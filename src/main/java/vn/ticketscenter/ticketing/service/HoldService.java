package vn.ticketscenter.ticketing.service;

import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;
import vn.ticketscenter.ticketing.dto.TicketingDtos.CreateHoldRequest;
import vn.ticketscenter.ticketing.dto.TicketingDtos.HoldItemRequest;
import vn.ticketscenter.ticketing.dto.TicketingDtos.HoldResponseDto;
import vn.ticketscenter.ticketing.repository.HoldRepository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class HoldService {
    private final TransactionManager transactions;
    private final HoldRepository holds;

    public HoldService(TransactionManager transactions, HoldRepository holds) {
        this.transactions = Objects.requireNonNull(transactions);
        this.holds = Objects.requireNonNull(holds);
    }

    public HoldResponseDto create(AuthenticatedAccount account, CreateHoldRequest request) {
        requireAccount(account);
        if (request == null || request.eventId() == null || request.items() == null) throw new IllegalArgumentException("eventId and items are required");
        List<HoldItemRequest> items = List.copyOf(request.items());
        int total = items.stream().mapToInt(HoldItemRequest::quantity).sum();
        if (items.isEmpty() || total < 1 || total > 8) throw new IllegalArgumentException("hold must contain between 1 and 8 tickets");
        if (items.stream().anyMatch(i -> i == null || i.zoneId() == null || i.quantity() < 1)) throw new IllegalArgumentException("invalid hold selection");
        return transactions.execute(DatabasePrincipal.BUYER,
                entityManager -> holds.create(entityManager, account.id(), request.eventId(), items));
    }

    public HoldResponseDto cancel(AuthenticatedAccount account, UUID holdId) {
        requireAccount(account);
        if (holdId == null) throw new IllegalArgumentException("holdId is required");
        return transactions.execute(DatabasePrincipal.BUYER,
                entityManager -> holds.release(entityManager, holdId, account.id()));
    }

    private static void requireAccount(AuthenticatedAccount account) {
        if (account == null) throw new SecurityException("authenticated account required");
    }
}
