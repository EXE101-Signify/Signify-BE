package fptu.exe202.signify.signifybe.features.call.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class VideoCallEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(VideoCallEventPublisher.class);
    private final SimpMessagingTemplate messaging;

    public VideoCallEventPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(VideoCallEvent event) {
        if (VideoCallEvent.INCOMING_CALL.equals(event.type())) {
            send(event.receiverId(), "/queue/calls/incoming", event);
        } else if (VideoCallEvent.CALL_STATUS_CHANGED.equals(event.type())) {
            send(event.callerId(), "/queue/calls/status", event);
            if (event.receiverId() != event.callerId()) {
                send(event.receiverId(), "/queue/calls/status", event);
            }
        }
    }

    private void send(long userId, String destination, VideoCallEvent event) {
        try {
            messaging.convertAndSendToUser(Long.toString(userId), destination, event);
        } catch (RuntimeException ex) {
            log.warn("Could not send call event {} to participant {}", event.eventId(), userId, ex);
        }
    }
}
