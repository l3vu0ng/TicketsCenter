package vn.ticketscenter.identity.service;

import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.dto.OrganizationDtos.CommissionRuleCommand;
import vn.ticketscenter.identity.dto.OrganizationDtos.CommissionRuleView;
import vn.ticketscenter.identity.dto.OrganizationDtos.MemberCommand;
import vn.ticketscenter.identity.dto.OrganizationDtos.MemberView;
import vn.ticketscenter.identity.dto.OrganizationDtos.MembershipView;
import vn.ticketscenter.identity.dto.OrganizationDtos.OrganizationRequestCommand;
import vn.ticketscenter.identity.dto.OrganizationDtos.OrganizationRequestView;
import vn.ticketscenter.identity.dto.OrganizationDtos.Page;
import vn.ticketscenter.identity.model.IdentityEnums.OrganizationRole;
import vn.ticketscenter.identity.repository.OrganizationRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class OrganizationService {

    private final TransactionManager transactions;
    private final OrganizationRepository organizations;
    private final Supplier<String> initialCommissionPolicy;

    public OrganizationService(TransactionManager transactions, OrganizationRepository organizations) {
        this(transactions, organizations, () -> AppConfig.get("organization.initialCommissionPolicy"));
    }

    public OrganizationService(
            TransactionManager transactions, OrganizationRepository organizations,
            Supplier<String> initialCommissionPolicy) {
        this.transactions = Objects.requireNonNull(transactions);
        this.organizations = Objects.requireNonNull(organizations);
        this.initialCommissionPolicy = Objects.requireNonNull(initialCommissionPolicy);
    }

    public UUID createRequest(AccountService.AuthenticatedAccount account, OrganizationRequestCommand command) {
        requireAccount(account);
        OrganizationRequestCommand valid = validateRequest(command);
        return transactions.execute(DatabasePrincipal.BUYER,
                entityManager -> organizations.createRequest(entityManager, account.id(), valid));
    }

    public Page<OrganizationRequestView> getMyRequests(
            AccountService.AuthenticatedAccount account, int page, int pageSize) {
        requireAccount(account);
        validatePage(page, pageSize);
        return transactions.execute(DatabasePrincipal.BUYER,
                entityManager -> organizations.findRequests(entityManager, account.id(), null, page, pageSize));
    }

    public Page<OrganizationRequestView> getAdminRequests(
            AccountService.AuthenticatedAccount account, String status, int page, int pageSize) {
        AuthorizationService.requireAdmin(account);
        validatePage(page, pageSize);
        String validStatus = status == null || status.isBlank() ? null : validateRequestStatus(status);
        return transactions.execute(DatabasePrincipal.ADMIN,
                entityManager -> organizations.findRequests(entityManager, null, validStatus, page, pageSize));
    }

    public OrganizationRequestView getAdminRequest(AccountService.AuthenticatedAccount account, UUID requestId) {
        AuthorizationService.requireAdmin(account);
        OrganizationRequestView result = transactions.execute(DatabasePrincipal.ADMIN,
                entityManager -> organizations.findRequest(entityManager, requireId(requestId)));
        if (result == null) throw new NoSuchElementException("organization request not found");
        return result;
    }

    public UUID approveRequest(AccountService.AuthenticatedAccount account, UUID requestId) {
        AuthorizationService.requireAdmin(account);
        String policy = initialCommissionPolicy.get();
        if (policy == null || policy.isBlank()) throw new IllegalStateException("initial commission policy is not configured");
        return transactions.execute(DatabasePrincipal.ADMIN,
                entityManager -> organizations.approveRequest(entityManager, requireId(requestId), account.id(), policy));
    }

    public void rejectRequest(AccountService.AuthenticatedAccount account, UUID requestId, String reason) {
        AuthorizationService.requireAdmin(account);
        String validReason = requiredText(reason, 1000, "rejection reason");
        boolean updated = transactions.execute(DatabasePrincipal.ADMIN,
                entityManager -> organizations.rejectRequest(entityManager, requireId(requestId), account.id(), validReason));
        if (!updated) throw new IllegalStateException("organization request is not pending");
    }

    public List<MembershipView> getMyMemberships(AccountService.AuthenticatedAccount account) {
        requireAccount(account);
        return transactions.execute(DatabasePrincipal.BUYER,
                entityManager -> organizations.findMemberships(entityManager, account.id()));
    }

    public List<MemberView> getMembers(AccountService.AuthenticatedAccount account, UUID organizationId) {
        requireAccount(account);
        UUID validOrganizationId = requireId(organizationId);
        return transactions.execute(DatabasePrincipal.MANAGER, entityManager -> {
            requireManager(entityManager, account.id(), validOrganizationId);
            return organizations.findMembers(entityManager, validOrganizationId);
        });
    }

    public void addOrReactivateMember(
            AccountService.AuthenticatedAccount account, UUID organizationId, MemberCommand command) {
        requireAccount(account);
        UUID validOrganizationId = requireId(organizationId);
        MemberCommand valid = validateMember(command);
        transactions.execute(DatabasePrincipal.MANAGER, entityManager -> {
            organizations.upsertMember(entityManager, account.id(), validOrganizationId, valid);
            return null;
        });
    }

    public void changeMemberRole(
            AccountService.AuthenticatedAccount account, UUID organizationId, UUID userId, String role) {
        mutateMember(account, organizationId, userId, validateRole(role), null);
    }

    public void setMemberActive(
            AccountService.AuthenticatedAccount account, UUID organizationId, UUID userId, boolean active) {
        mutateMember(account, organizationId, userId, null, active);
    }

    public List<CommissionRuleView> getCommissionRules(
            AccountService.AuthenticatedAccount account, UUID organizationId) {
        AuthorizationService.requireAdmin(account);
        return transactions.execute(DatabasePrincipal.ADMIN,
                entityManager -> organizations.findCommissionRules(entityManager, requireId(organizationId)));
    }

    public UUID createCommissionRule(
            AccountService.AuthenticatedAccount account, UUID organizationId, CommissionRuleCommand command) {
        AuthorizationService.requireAdmin(account);
        CommissionRuleCommand valid = validateCommissionRule(command);
        return transactions.execute(DatabasePrincipal.ADMIN,
                entityManager -> organizations.createCommissionRule(entityManager, requireId(organizationId), valid));
    }

    private void mutateMember(
            AccountService.AuthenticatedAccount account, UUID organizationId, UUID userId,
            String role, Boolean active) {
        requireAccount(account);
        UUID validOrganizationId = requireId(organizationId);
        UUID validUserId = requireId(userId);
        boolean updated = transactions.execute(DatabasePrincipal.MANAGER, entityManager ->
                organizations.updateMember(
                        entityManager, account.id(), validOrganizationId, validUserId, role, active));
        if (!updated) throw new NoSuchElementException("organization member not found");
    }

    private void requireManager(jakarta.persistence.EntityManager entityManager, UUID actorId, UUID organizationId) {
        if (!organizations.isActiveManager(entityManager, actorId, organizationId)) {
            throw new SecurityException("active manager membership required");
        }
    }

    private OrganizationRequestCommand validateRequest(OrganizationRequestCommand command) {
        if (command == null) throw new IllegalArgumentException("organization request is required");
        String phone = optionalText(command.contactPhone(), 30, "contact phone");
        if (phone != null && !phone.matches("[+0-9 .()\\-]{7,30}")) throw new IllegalArgumentException("contact phone is invalid");
        return new OrganizationRequestCommand(
                requiredText(command.name(), 200, "organization name"),
                AccountService.normalizeEmail(command.contactEmail()), phone,
                optionalText(command.description(), 2000, "description"));
    }

    private MemberCommand validateMember(MemberCommand command) {
        if (command == null) throw new IllegalArgumentException("member is required");
        return new MemberCommand(AccountService.normalizeEmail(command.email()), validateRole(command.role()));
    }

    private CommissionRuleCommand validateCommissionRule(CommissionRuleCommand command) {
        if (command == null || command.ratePercent() == null || command.fixedFee() == null
                || command.effectiveFrom() == null || command.effectiveTo() == null) {
            throw new IllegalArgumentException("commission rule fields are required");
        }
        if (command.ratePercent().compareTo(BigDecimal.ZERO) < 0 || command.fixedFee().compareTo(BigDecimal.ZERO) < 0
                || !command.effectiveFrom().isBefore(command.effectiveTo())) {
            throw new IllegalArgumentException("commission rule terms are invalid");
        }
        return command;
    }

    private String validateRole(String role) {
        try {
            return OrganizationRole.valueOf(requiredText(role, 30, "member role")).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("member role must be MANAGER or CHECK_IN_STAFF");
        }
    }

    private String validateRequestStatus(String status) {
        return switch (status.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "PENDING", "APPROVED", "REJECTED" -> status.trim().toUpperCase(java.util.Locale.ROOT);
            default -> throw new IllegalArgumentException("invalid organization request status");
        };
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("invalid pagination");
    }

    private void requireAccount(AccountService.AuthenticatedAccount account) {
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
