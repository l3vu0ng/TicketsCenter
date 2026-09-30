package vn.ticketscenter.identity.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.Query;
import jakarta.persistence.StoredProcedureQuery;
import vn.ticketscenter.identity.dto.OrganizationDtos.CommissionRuleCommand;
import vn.ticketscenter.identity.dto.OrganizationDtos.CommissionRuleView;
import vn.ticketscenter.identity.dto.OrganizationDtos.MemberCommand;
import vn.ticketscenter.identity.dto.OrganizationDtos.MemberView;
import vn.ticketscenter.identity.dto.OrganizationDtos.MembershipView;
import vn.ticketscenter.identity.dto.OrganizationDtos.OrganizationRequestCommand;
import vn.ticketscenter.identity.dto.OrganizationDtos.OrganizationRequestView;
import vn.ticketscenter.identity.dto.OrganizationDtos.Page;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OrganizationRepository {

    public UUID createRequest(EntityManager entityManager, UUID applicantId, OrganizationRequestCommand command) {
        UUID requestId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT dbo.tc_organization_requests(
                            id, applicant_id, name, contact_email, contact_phone, description)
                        VALUES (:id, :applicantId, :name, :email, :phone, :description)
                        """)
                .setParameter("id", requestId)
                .setParameter("applicantId", applicantId)
                .setParameter("name", command.name())
                .setParameter("email", command.contactEmail())
                .setParameter("phone", command.contactPhone())
                .setParameter("description", command.description())
                .executeUpdate();
        return requestId;
    }

    public Page<OrganizationRequestView> findRequests(
            EntityManager entityManager, UUID applicantId, String status, int page, int pageSize) {
        String scope = applicantId == null ? "" : " AND r.applicant_id = :applicantId";
        String statusFilter = status == null ? "" : " AND r.status = :status";
        Query rows = entityManager.createNativeQuery("""
                SELECT r.id, r.applicant_id, r.reviewer_id, r.organization_id, r.name,
                       r.contact_email, r.contact_phone, r.description, r.status,
                       r.requested_at, r.decided_at, r.rejection_reason
                FROM dbo.tc_organization_requests r
                WHERE 1 = 1
                """ + scope + statusFilter + " ORDER BY r.requested_at DESC, r.id OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY");
        Query count = entityManager.createNativeQuery(
                "SELECT COUNT_BIG(*) FROM dbo.tc_organization_requests r WHERE 1 = 1" + scope + statusFilter);
        bindRequestFilters(rows, applicantId, status);
        bindRequestFilters(count, applicantId, status);
        rows.setParameter("offset", (page - 1) * pageSize).setParameter("limit", pageSize);
        @SuppressWarnings("unchecked") List<Object[]> result = rows.getResultList();
        return new Page<>(result.stream().map(this::requestView).toList(), page, pageSize,
                ((Number) count.getSingleResult()).longValue());
    }

    public OrganizationRequestView findRequest(EntityManager entityManager, UUID requestId) {
        @SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT r.id, r.applicant_id, r.reviewer_id, r.organization_id, r.name,
                               r.contact_email, r.contact_phone, r.description, r.status,
                               r.requested_at, r.decided_at, r.rejection_reason
                        FROM dbo.tc_organization_requests r WHERE r.id = :id
                        """)
                .setParameter("id", requestId).getResultList();
        return rows.isEmpty() ? null : requestView(rows.getFirst());
    }

    public boolean rejectRequest(EntityManager entityManager, UUID requestId, UUID reviewerId, String reason) {
        int updated = entityManager.createNativeQuery("""
                        UPDATE dbo.tc_organization_requests WITH (UPDLOCK, HOLDLOCK)
                        SET reviewer_id = :reviewerId, status = 'REJECTED', decided_at = SYSUTCDATETIME(),
                            rejection_reason = :reason, version = version + 1
                        WHERE id = :requestId AND status = 'PENDING'
                        """)
                .setParameter("reviewerId", reviewerId)
                .setParameter("reason", reason)
                .setParameter("requestId", requestId)
                .executeUpdate();
        if (updated == 0) return false;
        entityManager.createNativeQuery("""
                        INSERT dbo.tc_audit_logs(actor_id, action, aggregate_type, aggregate_id, detail)
                        VALUES (:actorId, 'ORGANIZATION_REQUEST_REJECTED', 'ORGANIZATION_REQUEST', :requestId, :reason)
                        """)
                .setParameter("actorId", reviewerId)
                .setParameter("requestId", requestId)
                .setParameter("reason", reason)
                .executeUpdate();
        return true;
    }

    public UUID approveRequest(EntityManager entityManager, UUID requestId, UUID reviewerId, String policy) {
        StoredProcedureQuery query = entityManager.createStoredProcedureQuery("dbo.usp_ApproveOrganizationRequest")
                .registerStoredProcedureParameter("request_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("actor_id", UUID.class, ParameterMode.IN)
                .registerStoredProcedureParameter("initial_commission_policy", String.class, ParameterMode.IN)
                .setParameter("request_id", requestId)
                .setParameter("actor_id", reviewerId)
                .setParameter("initial_commission_policy", policy);
        query.execute();
        @SuppressWarnings("unchecked") List<Object[]> rows = query.getResultList();
        if (rows.isEmpty()) throw new IllegalStateException("organization approval returned no result");
        return (UUID) rows.getFirst()[0];
    }

    public boolean isActiveManager(EntityManager entityManager, UUID actorId, UUID organizationId) {
        Number count = (Number) entityManager.createNativeQuery("""
                        SELECT COUNT_BIG(*) FROM dbo.tc_organization_memberships
                        WHERE user_id = :actorId AND organization_id = :organizationId
                          AND role = 'MANAGER' AND active = 1
                        """)
                .setParameter("actorId", actorId)
                .setParameter("organizationId", organizationId)
                .getSingleResult();
        return count.longValue() > 0;
    }

    public List<MemberView> findMembers(EntityManager entityManager, UUID organizationId) {
        @SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT membership_id, organization_id, user_id, email, full_name,
                               role, active, user_status, joined_at
                        FROM dbo.vw_OrganizationMembers WHERE organization_id = :organizationId
                        ORDER BY full_name, user_id
                        """)
                .setParameter("organizationId", organizationId).getResultList();
        return rows.stream().map(this::memberView).toList();
    }

    public List<MembershipView> findMemberships(EntityManager entityManager, UUID userId) {
        @SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT m.organization_id, o.name, m.role, m.active
                        FROM dbo.tc_organization_memberships m
                        JOIN dbo.tc_organizations o ON o.id = m.organization_id
                        WHERE m.user_id = :userId ORDER BY o.name, o.id
                        """)
                .setParameter("userId", userId).getResultList();
        return rows.stream().map(row -> new MembershipView(
                (UUID) row[0], (String) row[1], (String) row[2], (Boolean) row[3])).toList();
    }

    public void upsertMember(
            EntityManager entityManager, UUID actorId, UUID organizationId, MemberCommand command) {
        UUID userId = findActiveUserId(entityManager, command.email(), false);
        lockUsers(entityManager, actorId, userId);
        lockOrganization(entityManager, organizationId);
        requireActiveManager(entityManager, actorId, organizationId);
        findActiveUserId(entityManager, command.email(), true);
        int updated = entityManager.createNativeQuery("""
                        UPDATE dbo.tc_organization_memberships
                        SET role = :role, active = 1, version = version + 1
                        WHERE organization_id = :organizationId AND user_id = :userId
                        """)
                .setParameter("role", command.role())
                .setParameter("organizationId", organizationId)
                .setParameter("userId", userId)
                .executeUpdate();
        if (updated == 0) {
            entityManager.createNativeQuery("""
                            INSERT dbo.tc_organization_memberships(user_id, organization_id, role)
                            VALUES (:userId, :organizationId, :role)
                            """)
                    .setParameter("userId", userId)
                    .setParameter("organizationId", organizationId)
                    .setParameter("role", command.role())
                    .executeUpdate();
        }
    }

    public boolean updateMember(
            EntityManager entityManager, UUID actorId, UUID organizationId, UUID userId, String role, Boolean active) {
        lockUsers(entityManager, actorId, userId);
        lockOrganization(entityManager, organizationId);
        requireActiveManager(entityManager, actorId, organizationId);
        String roleUpdate = role == null ? "" : ", role = :role";
        String activeUpdate = active == null ? "" : ", active = :active";
        Query query = entityManager.createNativeQuery("""
                UPDATE dbo.tc_organization_memberships
                SET version = version + 1
                """ + roleUpdate + activeUpdate + " WHERE organization_id = :organizationId AND user_id = :userId");
        query.setParameter("organizationId", organizationId).setParameter("userId", userId);
        if (role != null) query.setParameter("role", role);
        if (active != null) query.setParameter("active", active);
        return query.executeUpdate() == 1;
    }

    public List<CommissionRuleView> findCommissionRules(EntityManager entityManager, UUID organizationId) {
        @SuppressWarnings("unchecked") List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT id, organization_id, rate_percent, fixed_fee, effective_from, effective_to
                        FROM dbo.tc_commission_rules WHERE organization_id = :organizationId
                        ORDER BY effective_from DESC, id
                        """)
                .setParameter("organizationId", organizationId).getResultList();
        return rows.stream().map(row -> new CommissionRuleView(
                (UUID) row[0], (UUID) row[1], (BigDecimal) row[2], (BigDecimal) row[3],
                instant(row[4]), instant(row[5]))).toList();
    }

    public UUID createCommissionRule(EntityManager entityManager, UUID organizationId, CommissionRuleCommand command) {
        lockOrganization(entityManager, organizationId);
        UUID id = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT dbo.tc_commission_rules(
                            id, organization_id, rate_percent, fixed_fee, effective_from, effective_to)
                        VALUES (:id, :organizationId, :rate, :fixed, :effectiveFrom, :effectiveTo)
                        """)
                .setParameter("id", id).setParameter("organizationId", organizationId)
                .setParameter("rate", command.ratePercent()).setParameter("fixed", command.fixedFee())
                .setParameter("effectiveFrom", command.effectiveFrom())
                .setParameter("effectiveTo", command.effectiveTo()).executeUpdate();
        return id;
    }

    private void lockOrganization(EntityManager entityManager, UUID organizationId) {
        @SuppressWarnings("unchecked") List<UUID> rows = entityManager.createNativeQuery(
                        "SELECT id FROM dbo.tc_organizations WITH (UPDLOCK, HOLDLOCK) WHERE id = :id", UUID.class)
                .setParameter("id", organizationId).getResultList();
        if (rows.isEmpty()) throw new java.util.NoSuchElementException("organization not found");
    }

    private UUID findActiveUserId(EntityManager entityManager, String normalizedEmail, boolean locked) {
        String lock = locked ? " WITH (UPDLOCK, HOLDLOCK)" : "";
        @SuppressWarnings("unchecked") List<UUID> rows = entityManager.createNativeQuery("""
                        SELECT id FROM dbo.tc_users
                        """ + lock + """
                        WHERE normalized_email = :email AND status = 'ACTIVE'
                        """, UUID.class)
                .setParameter("email", normalizedEmail).getResultList();
        if (rows.isEmpty()) throw new java.util.NoSuchElementException("active user not found");
        return rows.getFirst();
    }

    private void lockUsers(EntityManager entityManager, UUID first, UUID second) {
        entityManager.createNativeQuery("""
                        SELECT id FROM dbo.tc_users WITH (UPDLOCK, HOLDLOCK)
                        WHERE id IN (:first, :second) ORDER BY id
                        """)
                .setParameter("first", first).setParameter("second", second).getResultList();
    }

    private void requireActiveManager(EntityManager entityManager, UUID actorId, UUID organizationId) {
        if (!isActiveManager(entityManager, actorId, organizationId)) {
            throw new SecurityException("active manager membership required");
        }
    }

    private void bindRequestFilters(Query query, UUID applicantId, String status) {
        if (applicantId != null) query.setParameter("applicantId", applicantId);
        if (status != null) query.setParameter("status", status);
    }

    private OrganizationRequestView requestView(Object[] row) {
        return new OrganizationRequestView(
                (UUID) row[0], (UUID) row[1], (UUID) row[2], (UUID) row[3], (String) row[4],
                (String) row[5], (String) row[6], (String) row[7], (String) row[8],
                instant(row[9]), instant(row[10]), (String) row[11]);
    }

    private MemberView memberView(Object[] row) {
        return new MemberView(
                (UUID) row[0], (UUID) row[1], (UUID) row[2], (String) row[3], (String) row[4],
                (String) row[5], (Boolean) row[6], (String) row[7], instant(row[8]));
    }

    private Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        return ((Timestamp) value).toInstant();
    }
}
