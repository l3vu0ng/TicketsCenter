package vn.ticketscenter.audit.model;

import jakarta.persistence.*;

@Entity
@Table(name = "tc_audit_logs", schema = "dbo")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "actor_id", nullable = true)
    private vn.ticketscenter.identity.model.User actor;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private java.util.UUID aggregateId;

    @Column(name = "detail", nullable = true)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private java.time.Instant createdAt;

    protected AuditLog() {
    }

    public Long getId() {
        return id;
    }
}
