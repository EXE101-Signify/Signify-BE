package fptu.exe202.signify.signifybe.features.ai.application;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallService;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

@Service
public class CallSpeechService {
    private static final Logger log = LoggerFactory.getLogger(CallSpeechService.class);
    private final VideoCallService calls;
    private final SimpMessagingTemplate messaging;
    private final Clock clock;

    public CallSpeechService(VideoCallService calls, SimpMessagingTemplate messaging, Clock clock) {
        this.calls = calls;
        this.messaging = messaging;
        this.clock = clock;
    }

    public SpeechEvent publish(long callId, CurrentUser actor, String text) {
        VideoCall call = calls.requireNonSigner(callId, actor);
        String transcript = text == null ? "" : text.strip();
        if (transcript.isEmpty() || transcript.length() > 2000)
            throw new IllegalArgumentException("Speech transcript must contain 1 to 2000 characters");
        SpeechEvent event = new SpeechEvent(UUID.randomUUID().toString(), "CALL_SPEECH_TRANSCRIPT",
                callId, actor.userId(), transcript, clock.millis());
        send(call.getCallerId(), callId, event);
        if (!call.getReceiverId().equals(call.getCallerId())) send(call.getReceiverId(), callId, event);
        return event;
    }

    private void send(long userId, long callId, SpeechEvent event) {
        try {
            messaging.convertAndSendToUser(Long.toString(userId), "/queue/calls/" + callId, event);
        } catch (RuntimeException ex) {
            log.warn("Could not send speech transcript {} to participant {}", event.eventId(), userId, ex);
        }
    }

    public record SpeechEvent(String eventId, String type, long callId, long speakerId,
                              String text, long timestamp) { }
}
