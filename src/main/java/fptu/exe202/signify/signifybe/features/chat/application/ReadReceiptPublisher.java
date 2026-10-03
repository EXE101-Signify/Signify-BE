package fptu.exe202.signify.signifybe.features.chat.application;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ReadReceiptPublisher {
    private static final Logger log = LoggerFactory.getLogger(ReadReceiptPublisher.class);
    private final PrivateChatAccessService access;
    private final SimpMessagingTemplate messaging;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(ReadReceiptEvent event) {
        try {
            long peer = access.unblockedPeer(event.conversationId(), event.userId());
            messaging.convertAndSendToUser(Long.toString(peer),
                    "/queue/conversations/" + event.conversationId(), event);
        } catch (RuntimeException ex) {
            log.warn("Could not deliver read receipt {}", event.eventId(), ex);
        }
    }
}
