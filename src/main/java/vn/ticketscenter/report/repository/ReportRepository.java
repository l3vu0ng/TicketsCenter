package vn.ticketscenter.report.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ReportRepository {
    public ReportPage find(EntityManager entityManager, ReportFilter filter) {
        ReportMetrics metrics = metrics(entityManager, filter);
        String order = switch (filter.sort()) {
            case "paidAt" -> "event_id";
            case "grossRevenue" -> "gross_revenue DESC, event_id";
            default -> "title, event_id";
        };
        String where = " WHERE (:organizationId IS NULL OR organization_id = :organizationId)"
                + " AND (:eventId IS NULL OR event_id = :eventId)";
        Number total = (Number) bind(entityManager.createNativeQuery(
                "SELECT COUNT_BIG(*) FROM dbo.vw_EventSalesReport" + where), filter).getSingleResult();
        @SuppressWarnings("unchecked") List<Object[]> rows = bind(entityManager.createNativeQuery("""
                SELECT event_id, organization_id, title, status, paid_order_count,
                       active_tickets, used_tickets, inactive_tickets, gross_revenue,
                       total_refund, total_commission, net_payable, is_settlement_snapshot
                FROM dbo.vw_EventSalesReport
                """ + where + " ORDER BY " + order
                + " OFFSET :offset ROWS FETCH NEXT :pageSize ROWS ONLY"), filter)
                .setParameter("offset", (filter.page() - 1) * filter.pageSize())
                .setParameter("pageSize", filter.pageSize()).getResultList();
        return new ReportPage(metrics, rows.stream().map(ReportRepository::event).toList(),
                filter.page(), filter.pageSize(), total.longValue());
    }

    public boolean isActiveManager(EntityManager entityManager, UUID actorId, UUID organizationId) {
        Number count = (Number) entityManager.createNativeQuery("""
                        SELECT COUNT_BIG(*) FROM dbo.tc_organization_memberships
                        WHERE user_id = :actorId AND organization_id = :organizationId
                          AND role = 'MANAGER' AND active = 1
                        """).setParameter("actorId", actorId).setParameter("organizationId", organizationId)
                .getSingleResult();
        return count.longValue() == 1;
    }

    private ReportMetrics metrics(EntityManager entityManager, ReportFilter filter) {
        Object[] revenue = (Object[]) bindOrganization(entityManager.createNativeQuery("""
                SELECT COALESCE(SUM(r.paid_order_count),0), COALESCE(SUM(r.gross_revenue),0),
                       COALESCE(SUM(r.refunded_amount),0), COALESCE(SUM(r.net_revenue),0)
                FROM dbo.tc_organizations o
                CROSS APPLY dbo.fn_GetOrganizationRevenue(o.id, :fromUtc, :toUtc) r
                WHERE (:organizationId IS NULL OR o.id = :organizationId)
                """), filter).setParameter("fromUtc", filter.from()).setParameter("toUtc", filter.to())
                .getSingleResult();
        @SuppressWarnings("unchecked") List<Object[]> cashRows = bindOrganization(entityManager.createNativeQuery("""
                SELECT f.flow_type, COALESCE(SUM(f.inflow_amount),0), COALESCE(SUM(f.outflow_amount),0)
                FROM dbo.tc_organizations o
                CROSS APPLY dbo.fn_GetOrganizationCashFlow(o.id, :fromUtc, :toUtc) f
                WHERE (:organizationId IS NULL OR o.id = :organizationId)
                GROUP BY f.flow_type
                """), filter).setParameter("fromUtc", filter.from()).setParameter("toUtc", filter.to())
                .getResultList();
        BigDecimal ticketCaptured = zero(), compensationCaptured = zero(), ticketRefunded = zero(), compensationRefunded = zero();
        for (Object[] row : cashRows) {
            switch ((String) row[0]) {
                case "TICKET_CAPTURE" -> ticketCaptured = money(row[1]);
                case "COMPENSATION_CAPTURE" -> compensationCaptured = money(row[1]);
                case "TICKET_REFUND" -> ticketRefunded = money(row[2]);
                case "COMPENSATION_REFUND" -> compensationRefunded = money(row[2]);
                default -> throw new IllegalStateException("unsupported cash-flow type");
            }
        }
        Object[] settlement = (Object[]) bind(entityManager.createNativeQuery("""
                SELECT COALESCE(SUM(total_commission),0), COALESCE(SUM(net_payable),0)
                FROM dbo.vw_EventSalesReport
                WHERE (:organizationId IS NULL OR organization_id = :organizationId)
                  AND (:eventId IS NULL OR event_id = :eventId)
                """), filter).getSingleResult();
        return new ReportMetrics(((Number) revenue[0]).longValue(), money(revenue[1]), money(revenue[2]),
                money(revenue[3]), ticketCaptured, compensationCaptured, ticketRefunded,
                compensationRefunded, ticketCaptured.add(compensationCaptured).subtract(ticketRefunded).subtract(compensationRefunded),
                money(settlement[0]), money(settlement[1]));
    }

    private static Query bind(Query query, ReportFilter filter) {
        return query.setParameter("organizationId", filter.organizationId()).setParameter("eventId", filter.eventId());
    }

    private static Query bindOrganization(Query query, ReportFilter filter) {
        return query.setParameter("organizationId", filter.organizationId());
    }

    private static EventReport event(Object[] row) {
        return new EventReport(vn.ticketscenter.config.util.InputParser.asUuid(row[0]), vn.ticketscenter.config.util.InputParser.asUuid(row[1]), (String) row[2], (String) row[3],
                ((Number) row[4]).longValue(), ((Number) row[5]).longValue(), ((Number) row[6]).longValue(),
                ((Number) row[7]).longValue(), money(row[8]), money(row[9]), money(row[10]), money(row[11]),
                row[12] instanceof Boolean value ? value : ((Number) row[12]).intValue() == 1);
    }

    private static BigDecimal money(Object value) {
        return value instanceof BigDecimal amount ? amount : new BigDecimal(value.toString());
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(0);
    }

    public record ReportFilter(UUID organizationId, UUID eventId, Instant from, Instant to,
                               int page, int pageSize, String sort) {
        private static final Set<String> SORTS = Set.of("eventTitle", "paidAt", "grossRevenue");

        public ReportFilter validate() {
            if (from == null || to == null || !from.isBefore(to)
                    || Duration.between(from, to).compareTo(Duration.ofDays(366)) > 0) {
                throw new IllegalArgumentException("report range must be positive and at most 366 days");
            }
            if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("invalid pagination");
            String validSort = sort == null || sort.isBlank() ? "eventTitle" : sort;
            if (!SORTS.contains(validSort)) throw new IllegalArgumentException("invalid report sort");
            return new ReportFilter(organizationId, eventId, from, to, page, pageSize, validSort);
        }

        public static ReportFilter scoped(UUID organizationId, ReportFilter requested) {
            if (requested.organizationId != null && !requested.organizationId.equals(organizationId)) {
                throw new SecurityException("report organization is outside the active scope");
            }
            return new ReportFilter(organizationId, requested.eventId, requested.from, requested.to,
                    requested.page, requested.pageSize, requested.sort).validate();
        }
    }

    public record ReportMetrics(long paidOrderCount, BigDecimal cohortGrossRevenue,
                                BigDecimal cohortRefundedAmount, BigDecimal cohortNetRevenue,
                                BigDecimal ticketCapturedAmount, BigDecimal compensationCapturedAmount,
                                BigDecimal ticketRefundedAmount, BigDecimal compensationRefundedAmount,
                                BigDecimal netCashFlow, BigDecimal commissionAmount, BigDecimal netPayable) {}

    public record EventReport(UUID eventId, UUID organizationId, String title, String status,
                              long paidOrderCount, long activeTickets, long usedTickets, long inactiveTickets,
                              BigDecimal grossRevenue, BigDecimal totalRefund, BigDecimal totalCommission,
                              BigDecimal netPayable, boolean settlementSnapshot) {}

    public record ReportPage(ReportMetrics metrics, List<EventReport> items, int page, int pageSize, long total) {}
}
