package fptu.exe202.signify.signifybe.features.notification.application;

import fptu.exe202.signify.signifybe.features.notification.domain.Notification;
import fptu.exe202.signify.signifybe.features.notification.domain.NotificationType;
import fptu.exe202.signify.signifybe.features.notification.domain.exception.NotificationException;
import fptu.exe202.signify.signifybe.features.notification.infrastructure.persistence.JpaNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationService {
    public static final int MAX_PAGE_SIZE = 100;

    private final JpaNotificationRepository repository;
    private final Clock clock;

    @PreAuthorize("principal.userId == #userId")
    @Transactional(readOnly = true)
    public Page<Notification> list(long userId, boolean unreadOnly, int page, int size) {
        validatePagination(page, size);
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return unreadOnly
                ? repository.findByUserIdAndReadFalse(userId, pageable)
                : repository.findByUserId(userId, pageable);
    }

    @PreAuthorize("principal.userId == #userId")
    @Transactional(readOnly = true)
    public long unreadCount(long userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    @PreAuthorize("principal.userId == #userId")
    @Transactional
    public Notification markRead(long userId, long notificationId) {
        Notification notification = repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(NotificationException::notFound);
        notification.markRead(clock.millis());
        return notification;
    }

    @PreAuthorize("principal.userId == #userId")
    @Transactional
    public int markAllRead(long userId) {
        return repository.markAllRead(userId, clock.millis());
    }

    @Transactional
    public Optional<Notification> create(long userId, NotificationType type, String title, String content,
                                         String referenceType, Long referenceId, String deduplicationKey) {
        if (deduplicationKey != null && repository.existsByDeduplicationKey(deduplicationKey)) {
            return Optional.empty();
        }
        return Optional.of(repository.save(new Notification(userId, type, title, content, referenceType,
                referenceId, deduplicationKey, clock.millis())));
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Invalid pagination");
        }
    }
}
