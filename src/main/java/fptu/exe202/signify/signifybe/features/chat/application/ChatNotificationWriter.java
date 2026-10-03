package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.notification.application.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Runs inside the message transaction, so a committed message has a durable notification. */
@Component
@RequiredArgsConstructor
public class ChatNotificationWriter {
    private final PrivateChatAccessService access;
    private final NotificationService notifications;

    @EventListener
    public void on(ChatEvent event) {
        if (!"MESSAGE_CREATED".equals(event.type())) return;
        long recipient = access.unblockedPeer(event.conversationId(), event.senderId());
        notifications.createChatMessage(recipient, event.messageId());
    }
}
