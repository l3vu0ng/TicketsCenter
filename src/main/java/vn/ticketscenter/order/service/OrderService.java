package vn.ticketscenter.order.service;

import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;
import vn.ticketscenter.order.dto.OrderDtos.OrderResult;
import vn.ticketscenter.order.repository.OrderRepository;

import java.util.Objects;
import java.util.UUID;

public final class OrderService {
    private final TransactionManager transactions;
    private final OrderRepository orders;

    public OrderService(TransactionManager transactions, OrderRepository orders) {
        this.transactions = Objects.requireNonNull(transactions);
        this.orders = Objects.requireNonNull(orders);
    }

    public OrderResult createFromHold(AuthenticatedAccount account, UUID holdId) {
        requireAccount(account);
        if (holdId == null) throw new IllegalArgumentException("holdId is required");
        return transactions.execute(DatabasePrincipal.BUYER,
                entityManager -> orders.createFromHold(entityManager, holdId, account.id()));
    }

    public OrderResult applyCoupon(AuthenticatedAccount account, UUID orderId, String couponCode) {
        requireAccount(account);
        if (orderId == null) throw new IllegalArgumentException("orderId is required");
        String code = couponCode == null || couponCode.isBlank() ? null : couponCode.trim().toUpperCase(java.util.Locale.ROOT);
        if (code != null && code.length() > 80) throw new IllegalArgumentException("coupon code is too long");
        return transactions.execute(DatabasePrincipal.BUYER,
                entityManager -> orders.applyCoupon(entityManager, orderId, account.id(), code));
    }

    private void requireAccount(AuthenticatedAccount account) {
        if (account == null || account.id() == null) throw new SecurityException("authenticated user required");
    }
}
