package fptu.exe202.signify.signifybe.features.ai.application;

import fptu.exe202.signify.signifybe.features.ai.api.dto.AiPredictionResponse;
import fptu.exe202.signify.signifybe.features.ai.domain.exception.AiException;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallService;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class AiPredictionService {
    private static final Logger log = LoggerFactory.getLogger(AiPredictionService.class);
    private final VideoCallService calls;
    private final AiService ai;
    private final SimpMessagingTemplate messaging;

    public AiPredictionService(VideoCallService calls, AiService ai, SimpMessagingTemplate messaging) {
        this.calls = calls;
        this.ai = ai;
        this.messaging = messaging;
    }

    public AiPredictionEvent predict(long callId, CurrentUser actor, byte[] image, MediaType imageType) {
        calls.validateActiveCall(callId, actor);
        AiPredictionResponse prediction = ai.predict(image, imageType);
        if (prediction == null || !prediction.isValid()) throw AiException.invalidResponse();

        // A call may have ended while the external AI request was in flight.
        VideoCall call = calls.validateActiveCall(callId, actor);
        AiPredictionEvent event = AiPredictionEvent.from(call.getConversationId(), callId, prediction);
        String destination = "/queue/calls/" + callId;
        send(call.getCallerId(), destination, event);
        if (!call.getReceiverId().equals(call.getCallerId())) {
            send(call.getReceiverId(), destination, event);
        }
        return event;
    }

    private void send(long userId, String destination, AiPredictionEvent event) {
        try {
            messaging.convertAndSendToUser(Long.toString(userId), destination, event);
        } catch (RuntimeException ex) {
            log.warn("Could not send AI prediction event {} to participant {}", event.eventId(), userId, ex);
        }
    }
}
