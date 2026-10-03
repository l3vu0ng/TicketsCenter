package vn.ticketscenter.audit.service;

import vn.ticketscenter.audit.repository.AuditRepository;
import vn.ticketscenter.audit.repository.AuditRepository.AuditEntry;
import vn.ticketscenter.audit.repository.AuditRepository.AuditFilter;
import vn.ticketscenter.audit.repository.AuditRepository.AuditPage;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;
import vn.ticketscenter.identity.service.AuthorizationService;

import java.util.Objects;

public final class AuditService {
    private final TransactionManager transactions;
    private final AuditRepository audit;

    public AuditService(TransactionManager transactions, AuditRepository audit) {
        this.transactions = Objects.requireNonNull(transactions);
        this.audit = Objects.requireNonNull(audit);
    }

    public AuditPage get(AuthenticatedAccount account, AuditFilter filter) {
        AuthorizationService.requireAdmin(account);
        return transactions.execute(DatabasePrincipal.ADMIN, entityManager -> audit.find(entityManager, filter.validate()));
    }

    public AuditEntry getOne(AuthenticatedAccount account, long id) {
        AuthorizationService.requireAdmin(account);
        if (id < 1) throw new IllegalArgumentException("invalid audit id");
        return transactions.execute(DatabasePrincipal.ADMIN, entityManager -> audit.findOne(entityManager, id));
    }
}
