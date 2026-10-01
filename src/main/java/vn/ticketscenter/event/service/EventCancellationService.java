package vn.ticketscenter.event.service;

import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.event.dto.EventCancellationDtos.CancellationProgress;
import vn.ticketscenter.event.repository.EventCancellationRepository;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;
import vn.ticketscenter.identity.service.AuthorizationService;

import java.util.Objects;
import java.util.UUID;

public final class EventCancellationService {
    private final TransactionManager transactions;
    private final EventCancellationRepository repository;

    public EventCancellationService(TransactionManager transactions, EventCancellationRepository repository) {
        this.transactions = Objects.requireNonNull(transactions);
        this.repository = Objects.requireNonNull(repository);
    }

    public CancellationProgress cancel(AuthenticatedAccount account, UUID eventId) {
        AuthorizationService.requireAdmin(account);
        Objects.requireNonNull(eventId, "eventId is required");
        return transactions.execute(DatabasePrincipal.ADMIN, entityManager -> {
            repository.cancel(entityManager, eventId, account.id());
            return repository.progress(entityManager, eventId);
        });
    }

    public CancellationProgress progress(AuthenticatedAccount account, UUID eventId) {
        AuthorizationService.requireAdmin(account);
        Objects.requireNonNull(eventId, "eventId is required");
        return transactions.execute(DatabasePrincipal.ADMIN,
                entityManager -> repository.progress(entityManager, eventId));
    }
}
