package fptu.exe202.signify.signifybe.features.ai.application;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallEvent;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallService;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCallStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiRecognitionModeService {
    private static final Logger log = LoggerFactory.getLogger(AiRecognitionModeService.class);
    private final ConcurrentHashMap<Long, ModeUpdate> modes = new ConcurrentHashMap<>();
    private final VideoCallService calls;
    private final SimpMessagingTemplate messaging;
    private final Clock clock;

    public AiRecognitionModeService(VideoCallService calls, SimpMessagingTemplate messaging, Clock clock) {
        this.calls = calls;
        this.messaging = messaging;
        this.clock = clock;
    }

    public ModeUpdate current(long callId, CurrentUser actor) {
        calls.validateActiveCall(callId, actor);
        return modes.getOrDefault(callId, new ModeUpdate("AI_RECOGNITION_MODE", callId, Mode.ALPHABET, 0));
    }

    public ModeUpdate change(long callId, CurrentUser actor, Mode mode) {
        if (mode == null) throw new IllegalArgumentException("Recognition mode is required");
        VideoCall call = calls.requireSignerAccount(callId, actor);
        return modes.compute(callId, (ignored, previous) -> {
            calls.requireSignerAccount(callId, actor);
            if (previous != null && previous.mode() == mode) return previous;
            long timestamp = Math.max(clock.millis(), previous == null ? 1 : previous.timestamp() + 1);
            ModeUpdate update = new ModeUpdate("AI_RECOGNITION_MODE", callId, mode, timestamp);
            send(call.getCallerId(), callId, update);
            if (!call.getReceiverId().equals(call.getCallerId())) send(call.getReceiverId(), callId, update);
            return update;
        });
    }

    private void send(long userId, long callId, ModeUpdate update) {
        try {
            messaging.convertAndSendToUser(Long.toString(userId), "/queue/calls/" + callId, update);
        } catch (RuntimeException ex) {
            log.warn("Could not send recognition mode for call {} to participant {}", callId, userId, ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCallStatus(VideoCallEvent event) {
        if (VideoCallEvent.CALL_STATUS_CHANGED.equals(event.type())
                && event.status() != VideoCallStatus.ACCEPTED) modes.remove(event.callId());
    }

    public enum Mode { ALPHABET, VSL_WORDS, VSL_STREAMING }

    public record ModeUpdate(String type, long callId, Mode mode, long timestamp) { }
}
