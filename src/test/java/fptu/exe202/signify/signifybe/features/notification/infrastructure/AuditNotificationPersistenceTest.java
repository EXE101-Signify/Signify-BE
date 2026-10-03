package fptu.exe202.signify.signifybe.features.notification.infrastructure;

import fptu.exe202.signify.signifybe.features.audit.domain.AdminAuditAction;
import fptu.exe202.signify.signifybe.features.audit.domain.AdminAuditLog;
import fptu.exe202.signify.signifybe.features.audit.infrastructure.persistence.JpaAdminAuditLogRepository;
import fptu.exe202.signify.signifybe.features.notification.domain.Notification;
import fptu.exe202.signify.signifybe.features.notification.domain.NotificationType;
import fptu.exe202.signify.signifybe.features.notification.infrastructure.persistence.JpaNotificationRepository;
import fptu.exe202.signify.signifybe.features.user.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.liquibase.enabled=true"
})
class AuditNotificationPersistenceTest {
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JpaNotificationRepository notifications;
    @Autowired JpaAdminAuditLogRepository auditLogs;

    @Test
    void migrationAndJpaMappingsPersistAuditAndNotification() {
        User user = new User("admin@example.com", "Admin", "User", 1_000L);
        entityManager.persist(user);
        entityManager.flush();

        Notification notification = notifications.saveAndFlush(new Notification(user.getId(),
                NotificationType.MISSED_CALL, "Missed call", "content", "VIDEO_CALL", 5L,
                "missed-call:5", 2_000L));
        AdminAuditLog auditLog = auditLogs.saveAndFlush(new AdminAuditLog(user.getId(),
                AdminAuditAction.BAN_USER, "USER", 2L, "reason", Collections.emptyMap(), 2_000L));

        assertNotNull(notification.getId());
        assertNotNull(auditLog.getId());
        assertNotNull(jdbcTemplate.queryForObject("""
                select table_name from information_schema.tables
                where table_name = 'EVENT_PUBLICATION_ARCHIVE'
                """, String.class));
        assertNotNull(jdbcTemplate.queryForObject("""
                select column_name from information_schema.columns
                where table_name = 'EVENT_PUBLICATION' and column_name = 'COMPLETION_ATTEMPTS'
                """, String.class));
    }
}
