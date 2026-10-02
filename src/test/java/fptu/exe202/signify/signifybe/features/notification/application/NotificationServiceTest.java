package fptu.exe202.signify.signifybe.features.notification.application;

import fptu.exe202.signify.signifybe.features.notification.domain.Notification;
import fptu.exe202.signify.signifybe.features.notification.domain.NotificationType;
import fptu.exe202.signify.signifybe.features.notification.domain.exception.NotificationException;
import fptu.exe202.signify.signifybe.features.notification.infrastructure.persistence.JpaNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    private static final long NOW = 2_000L;

    @Mock JpaNotificationRepository repository;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));
    }

    @Test
    void listUnreadOnlyNeverReturnsAnotherUsersNotifications() {
        Notification notification = notification(7L);
        when(repository.findByUserIdAndReadFalse(eq(7L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification)));

        Page<Notification> result = service.list(7L, true, 0, 20);

        assertEquals(List.of(notification), result.getContent());
        verify(repository).findByUserIdAndReadFalse(eq(7L), any(Pageable.class));
        verify(repository, never()).findByUserId(anyLong(), any());
    }

    @Test
    void markReadScopesLookupToAuthenticatedUserAndIsIdempotent() {
        Notification notification = notification(7L);
        when(repository.findByIdAndUserId(12L, 7L)).thenReturn(Optional.of(notification));

        Notification first = service.markRead(7L, 12L);
        Notification second = service.markRead(7L, 12L);

        assertSame(notification, first);
        assertSame(notification, second);
        assertTrue(notification.isRead());
        assertEquals(NOW, notification.getReadAt());
    }

    @Test
    void markReadHidesNotificationsOwnedByAnotherUser() {
        when(repository.findByIdAndUserId(12L, 7L)).thenReturn(Optional.empty());

        assertThrows(NotificationException.class, () -> service.markRead(7L, 12L));
    }

    @Test
    void duplicateEventKeyDoesNotCreateAnotherNotification() {
        when(repository.existsByDeduplicationKey("missed-call:4")).thenReturn(true);

        Optional<Notification> result = service.create(7L, NotificationType.MISSED_CALL, "Missed call", "content",
                "VIDEO_CALL", 4L, "missed-call:4");

        assertTrue(result.isEmpty());
        verify(repository, never()).save(any());
    }

    @Test
    void markAllReadUsesOneScopedBulkUpdate() {
        when(repository.markAllRead(7L, NOW)).thenReturn(3);

        assertEquals(3, service.markAllRead(7L));
    }

    private Notification notification(long userId) {
        return new Notification(userId, NotificationType.MISSED_CALL, "Missed call", "content",
                "VIDEO_CALL", 4L, "missed-call:4", 1_000L);
    }
}
