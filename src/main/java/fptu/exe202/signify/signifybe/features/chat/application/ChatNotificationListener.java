package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.notification.application.NotificationService;
import fptu.exe202.signify.signifybe.features.notification.domain.Notification;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(ChatNotificationListener.class);
    private final PrivateChatAccessService access;
    private final NotificationService notifications;
    private final SimpMessagingTemplate messaging;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(ChatEvent event) {
        if (!"MESSAGE_CREATED".equals(event.type())) return;
        try {
            long recipient = access.unblockedPeer(event.conversationId(), event.senderId());
            notifications.findChatMessage(recipient, event.messageId()).ifPresent(notification -> {
                try {
                    messaging.convertAndSendToUser(Long.toString(recipient), "/queue/notifications",
                            NotificationEvent.of(notification));
                } catch (RuntimeException ex) {
                    log.warn("Could not push notification {}", notification.getId(), ex);
                }
            });
        } catch (RuntimeException ex) {
            log.warn("Could not push direct-message notification for message {}", event.messageId(), ex);
        }
    }

    public record NotificationEvent(String eventId, String type, long id, long userId, String notificationType,
                                    String title, String content, String referenceType, Long referenceId,
                                    long createdAt) {
        static NotificationEvent of(Notification notification) {
            return new NotificationEvent("NOTIFICATION_CREATED:" + notification.getId(),
                    "NOTIFICATION_CREATED", notification.getId(),
                    notification.getUserId(), notification.getType().name(), notification.getTitle(),
                    notification.getContent(), notification.getReferenceType(),
                    notification.getReferenceId(), notification.getCreatedAt());
        }
    }
}
