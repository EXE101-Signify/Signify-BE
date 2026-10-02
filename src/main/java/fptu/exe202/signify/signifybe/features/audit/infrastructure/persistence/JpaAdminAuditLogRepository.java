package fptu.exe202.signify.signifybe.features.audit.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.audit.domain.AdminAuditAction;
import fptu.exe202.signify.signifybe.features.audit.domain.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    @Query("""
            select log from AdminAuditLog log
            where (:adminId is null or log.adminId = :adminId)
              and (:action is null or log.action = :action)
              and (:targetType is null or log.targetType = :targetType)
              and (:targetId is null or log.targetId = :targetId)
              and (:fromTime is null or log.createdAt >= :fromTime)
              and (:toTime is null or log.createdAt <= :toTime)
            """)
    Page<AdminAuditLog> search(@Param("adminId") Long adminId,
                               @Param("action") AdminAuditAction action,
                               @Param("targetType") String targetType,
                               @Param("targetId") Long targetId,
                               @Param("fromTime") Long fromTime,
                               @Param("toTime") Long toTime,
                               Pageable pageable);
}
