package vn.ticketscenter.fulfillment.service;

import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.fulfillment.repository.RefundRepository;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;
import vn.ticketscenter.identity.service.AuthorizationService;

import java.util.Objects;
import java.util.UUID;

public final class RefundService {
    private final TransactionManager transactions;
    private final RefundRepository repository;

    public RefundService(TransactionManager transactions, RefundRepository repository) {
        this.transactions = Objects.requireNonNull(transactions);
        this.repository = Objects.requireNonNull(repository);
    }

    public void retryCustomer(AuthenticatedAccount account, UUID requestId) {
        AuthorizationService.requireAdmin(account);
        Objects.requireNonNull(requestId, "requestId is required");
        transactions.execute(DatabasePrincipal.ADMIN, entityManager -> {
            repository.retryCustomer(entityManager, requestId, account.id());
            return null;
        });
    }

    public void retryCompensation(AuthenticatedAccount account, UUID paymentId) {
        AuthorizationService.requireAdmin(account);
        Objects.requireNonNull(paymentId, "paymentId is required");
        transactions.execute(DatabasePrincipal.WORKER, entityManager -> {
            repository.retryCompensation(entityManager, paymentId);
            return null;
        });
    }
}
