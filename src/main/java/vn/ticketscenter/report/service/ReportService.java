package vn.ticketscenter.report.service;

import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;
import vn.ticketscenter.report.repository.ReportRepository;
import vn.ticketscenter.report.repository.ReportRepository.ReportFilter;
import vn.ticketscenter.report.repository.ReportRepository.ReportPage;

import java.util.Objects;
import java.util.UUID;

public final class ReportService {
    private final TransactionManager transactions;
    private final ReportRepository reports;

    public ReportService(TransactionManager transactions, ReportRepository reports) {
        this.transactions = Objects.requireNonNull(transactions);
        this.reports = Objects.requireNonNull(reports);
    }

    public ReportPage get(AuthenticatedAccount account, UUID organizationScope, ReportFilter requested) {
        if (account == null || account.id() == null) throw new SecurityException("authenticated user required");
        ReportFilter filter = (account.admin() && organizationScope == null)
                ? requested.validate() : ReportFilter.scoped(Objects.requireNonNull(organizationScope), requested);
        DatabasePrincipal principal = account.admin() ? DatabasePrincipal.ADMIN : DatabasePrincipal.MANAGER;
        return transactions.execute(principal, entityManager -> {
            if (!account.admin() && !reports.isActiveManager(entityManager, account.id(), filter.organizationId())) {
                throw new SecurityException("active manager membership required");
            }
            return reports.find(entityManager, filter);
        });
    }
}
