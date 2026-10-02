package fptu.exe202.signify.signifybe.features.notification.infrastructure.persistence;

import fptu.exe202.signify.signifybe.features.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface JpaNotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByUserId(long userId, Pageable pageable);
    Page<Notification> findByUserIdAndReadFalse(long userId, Pageable pageable);
    Optional<Notification> findByIdAndUserId(long id, long userId);
    long countByUserIdAndReadFalse(long userId);
    boolean existsByDeduplicationKey(String deduplicationKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification notification
               set notification.read = true, notification.readAt = :readAt
             where notification.userId = :userId and notification.read = false
            """)
    int markAllRead(@Param("userId") long userId, @Param("readAt") long readAt);
}
