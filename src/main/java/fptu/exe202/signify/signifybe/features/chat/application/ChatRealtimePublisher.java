package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationParticipantRepository;
import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationRepository;
import fptu.exe202.signify.signifybe.features.chat.domain.ConversationType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatRealtimePublisher {
    private static final Logger log = LoggerFactory.getLogger(ChatRealtimePublisher.class);
    private final SimpMessagingTemplate messaging;
    private final ConversationRepository conversations;
    private final ConversationParticipantRepository participants;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(ChatEvent event) {
        try {
            var conversation = conversations.findById(event.conversationId()).orElse(null);
            var active = participants.findActiveByConversationId(event.conversationId());
            if (conversation == null || !ConversationType.PRIVATE.name().equalsIgnoreCase(conversation.getType())
                    || active.size() != 2 || active.get(0).getUserId().equals(active.get(1).getUserId())) return;
            for (var participant : active) {
                try {
                    messaging.convertAndSendToUser(Long.toString(participant.getUserId()),
                            "/queue/conversations/" + event.conversationId(), event);
                } catch (RuntimeException ex) {
                    log.warn("Could not send chat event {} to participant {}", event.eventId(), participant.getUserId(), ex);
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Could not publish chat event {}", event.eventId(), ex);
        }
    }
}
