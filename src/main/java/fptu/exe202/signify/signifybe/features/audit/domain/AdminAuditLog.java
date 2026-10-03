package fptu.exe202.signify.signifybe.features.audit.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "admin_audit_logs")
public class AdminAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "admin_audit_log_id")
    @SequenceGenerator(name = "admin_audit_log_id", sequenceName = "admin_audit_log_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private AdminAuditAction action;

    @Column(name = "target_type", length = 50)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(columnDefinition = "text")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    protected AdminAuditLog() { }

    public AdminAuditLog(long adminId, AdminAuditAction action, String targetType, Long targetId,
                         String reason, Map<String, Object> metadata, long createdAt) {
        this.adminId = adminId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = normalize(reason);
        this.metadata = metadata;
        this.createdAt = createdAt;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.strip();
    }

    public Long getId() { return id; }
    public Long getAdminId() { return adminId; }
    public AdminAuditAction getAction() { return action; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getReason() { return reason; }
    public Map<String, Object> getMetadata() { return metadata; }
    public Long getCreatedAt() { return createdAt; }
}
