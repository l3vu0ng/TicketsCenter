package vn.ticketscenter.service.identity;

import vn.ticketscenter.model.ModelEnums;
import vn.ticketscenter.transaction.DatabasePrincipal;
import vn.ticketscenter.transaction.TransactionManager;

import java.util.Set;
import java.util.UUID;

public final class AuthorizationService {

    private final TransactionManager transactions;

    public AuthorizationService(TransactionManager transactions) {
        this.transactions = transactions;
    }

    public static void requireOwner(UUID actorId, UUID ownerId) {
        if (!actorId.equals(ownerId)) throw new SecurityException("resource is outside actor scope");
    }

    public static void requireAdmin(AccountService.AuthenticatedAccount account) {
        if (!account.admin()) throw new SecurityException("admin role is required");
    }

    public void requireActiveMembership(UUID actorId, UUID organizationId, Set<ModelEnums.OrganizationRole> roles) {
        boolean allowed = transactions.execute(DatabasePrincipal.BUYER, entityManager ->
                entityManager.createQuery("""
                                select count(m) from OrganizationMembership m
                                where m.user.id = :actorId and m.organization.id = :organizationId
                                  and m.active = true and m.role in :roles
                                """, Long.class)
                        .setParameter("actorId", actorId)
                        .setParameter("organizationId", organizationId)
                        .setParameter("roles", roles)
                        .getSingleResult() > 0);
        if (!allowed) throw new SecurityException("active organization membership is required");
    }
}
