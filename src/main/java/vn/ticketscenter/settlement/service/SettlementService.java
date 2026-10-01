package vn.ticketscenter.settlement.service;

import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;
import vn.ticketscenter.identity.service.AuthorizationService;
import vn.ticketscenter.settlement.dto.SettlementDtos.PayoutBalance;
import vn.ticketscenter.settlement.dto.SettlementDtos.SettlementBlocker;
import vn.ticketscenter.settlement.dto.SettlementDtos.SettlementSnapshot;
import vn.ticketscenter.settlement.integration.PayoutGateway;
import vn.ticketscenter.settlement.repository.SettlementRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SettlementService {
    private final TransactionManager transactions;
    private final SettlementRepository repository;
    private final PayoutGateway gateway;

    public SettlementService(TransactionManager transactions, SettlementRepository repository, PayoutGateway gateway) {
        this.transactions = Objects.requireNonNull(transactions);
        this.repository = Objects.requireNonNull(repository);
        this.gateway = Objects.requireNonNull(gateway);
    }

    public SettlementSnapshot recalculate(AuthenticatedAccount account, UUID eventId) {
        requireAdmin(account);
        Objects.requireNonNull(eventId, "eventId is required");
        return transactions.execute(DatabasePrincipal.ADMIN,
                entityManager -> repository.recalculate(entityManager, eventId, account.id()));
    }

    public PayoutBalance confirm(AuthenticatedAccount account, UUID settlementId) {
        requireAdmin(account);
        Objects.requireNonNull(settlementId, "settlementId is required");
        return transactions.execute(DatabasePrincipal.ADMIN,
                entityManager -> repository.confirm(entityManager, settlementId, account.id()));
    }

    public List<SettlementBlocker> blockers(AuthenticatedAccount account, UUID eventId) {
        requireAdmin(account);
        Objects.requireNonNull(eventId, "eventId is required");
        return transactions.execute(DatabasePrincipal.ADMIN,
                entityManager -> repository.blockers(entityManager, eventId));
    }

    public PayoutBalance balance(AuthenticatedAccount account, UUID settlementId) {
        requireAdmin(account);
        Objects.requireNonNull(settlementId, "settlementId is required");
        return transactions.execute(DatabasePrincipal.ADMIN,
                entityManager -> repository.balance(entityManager, settlementId));
    }

    public PayoutBalance payout(AuthenticatedAccount account, UUID settlementId,
                                UUID payoutId, BigDecimal amount) {
        requireAdmin(account);
        Objects.requireNonNull(settlementId, "settlementId is required");
        Objects.requireNonNull(payoutId, "payoutId is required");
        if (amount == null || amount.signum() <= 0 || amount.scale() > 0) {
            throw new IllegalArgumentException("amount must be positive whole VND");
        }
        String reference = gateway.reference(payoutId);
        transactions.execute(DatabasePrincipal.ADMIN, entityManager -> repository.recordPayout(
                entityManager, settlementId, payoutId, account.id(), amount, reference, "PENDING"));

        PayoutGateway.Result result = gateway.submit(payoutId, amount);
        if (!payoutId.equals(result.payoutId()) || amount.compareTo(result.amount()) != 0
                || !reference.equals(result.reference())) {
            throw new IllegalStateException("payout gateway result does not match reserved payout");
        }
        return transactions.execute(DatabasePrincipal.ADMIN, entityManager -> repository.recordPayout(
                entityManager, settlementId, payoutId, account.id(), amount, reference, result.status().name()));
    }

    private static void requireAdmin(AuthenticatedAccount account) {
        AuthorizationService.requireAdmin(account);
    }
}
