package fptu.exe202.signify.signifybe.features.chat.application;

import fptu.exe202.signify.signifybe.features.chat.application.port.out.ConversationParticipantRepository;
import fptu.exe202.signify.signifybe.features.chat.domain.exception.ConversationException;
import fptu.exe202.signify.signifybe.features.user.domain.exception.BlockException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PresenceEventDispatcher {
    private static final Logger log = LoggerFactory.getLogger(PresenceEventDispatcher.class);
    private final ConversationParticipantRepository participants;
    private final PrivateChatAccessService access;
    private final SimpMessagingTemplate messaging;

    public void dispatch(PresenceEvent event) {
        for (Long conversationId : participants.findConversationIdsByUserId(event.userId())) {
            try {
                long peer = access.unblockedPeer(conversationId, event.userId());
                messaging.convertAndSendToUser(Long.toString(peer),
                        "/queue/conversations/" + conversationId, event);
            } catch (ConversationException | BlockException ignored) {
                // Group, inactive membership, or blocked pair cannot observe presence.
            } catch (RuntimeException ex) {
                log.warn("Could not deliver presence for user {} in conversation {}", event.userId(), conversationId, ex);
            }
        }
    }
}
