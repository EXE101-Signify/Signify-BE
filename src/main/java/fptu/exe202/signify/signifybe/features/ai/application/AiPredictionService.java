package fptu.exe202.signify.signifybe.features.ai.application;

import fptu.exe202.signify.signifybe.features.ai.api.dto.AiPredictionResponse;
import fptu.exe202.signify.signifybe.features.ai.domain.exception.AiException;
import fptu.exe202.signify.signifybe.features.ai.infrastructure.AiFrameRejectedException;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallService;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import fptu.exe202.signify.signifybe.features.call.domain.exception.VideoCallException;
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
    private final StableLetterTracker letters;
    private final AiTextService text;
    private final SimpMessagingTemplate messaging;

    public AiPredictionService(VideoCallService calls, AiService ai, StableLetterTracker letters, AiTextService text,
                               SimpMessagingTemplate messaging) {
        this.calls = calls;
        this.ai = ai;
        this.letters = letters;
        this.text = text;
        this.messaging = messaging;
    }

    public AiPredictionEvent predict(long callId, CurrentUser actor, byte[] image, MediaType imageType) {
        VideoCall sourceCall = calls.validateActiveCall(callId, actor);
        if (actor.userId() != sourceCall.getReceiverId()) throw VideoCallException.forbidden();
        AiPredictionResponse prediction;
        try {
            prediction = ai.predict(image, imageType);
        } catch (AiFrameRejectedException ex) {
            calls.validateActiveCall(callId, actor);
            if (ex.isNoHand()) letters.noHand(callId);
            else letters.observe(callId, null);
            return null;
        } catch (AiException ex) {
            letters.observe(callId, null);
            throw ex;
        }
        if (prediction == null || !prediction.isValid()) {
            calls.validateActiveCall(callId, actor);
            letters.observe(callId, null);
            return null;
        }

        // A call may have ended while the external AI request was in flight.
        VideoCall call = calls.validateActiveCall(callId, actor);
        AiPredictionResponse accepted = letters.observe(callId, prediction);
        if (accepted == null) return null;
        text.appendAccepted(callId, actor, accepted.letter());
        AiPredictionEvent event = AiPredictionEvent.from(call.getConversationId(), callId, accepted);
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
