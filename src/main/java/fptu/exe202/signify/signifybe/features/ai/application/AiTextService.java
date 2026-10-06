package fptu.exe202.signify.signifybe.features.ai.application;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallEvent;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallService;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCall;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCallStatus;
import fptu.exe202.signify.signifybe.features.call.domain.exception.VideoCallException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** In-memory spelling buffer, isolated by call ID and mutated only by the authenticated signer. */
@Service
public class AiTextService {
    private static final Logger log = LoggerFactory.getLogger(AiTextService.class);
    private final ConcurrentHashMap<Long, String> textByCall = new ConcurrentHashMap<>();
    private final VideoCallService calls;
    private final SimpMessagingTemplate messaging;
    private final Clock clock;

    public AiTextService(VideoCallService calls, SimpMessagingTemplate messaging, Clock clock) {
        this.calls = calls;
        this.messaging = messaging;
        this.clock = clock;
    }

    /** Called only with a letter accepted by StableLetterTracker. */
    public AiTextUpdateEvent appendAccepted(long callId, CurrentUser actor, String letter) {
        if (letter == null || !letter.matches("[A-Z]")) throw new IllegalArgumentException("Accepted letter must be A-Z");
        return mutate(callId, actor, Action.APPEND, letter);
    }

    public AiTextUpdateEvent space(long callId, CurrentUser actor) {
        return mutate(callId, actor, Action.SPACE, null);
    }

    public AiTextUpdateEvent delete(long callId, CurrentUser actor) {
        return mutate(callId, actor, Action.DELETE, null);
    }

    public AiTextUpdateEvent clear(long callId, CurrentUser actor) {
        return mutate(callId, actor, Action.CLEAR, null);
    }

    private AiTextUpdateEvent mutate(long callId, CurrentUser actor, Action action, String letter) {
        VideoCall call = requireSigner(callId, actor);
        AtomicReference<AiTextUpdateEvent> result = new AtomicReference<>();
        textByCall.compute(callId, (ignored, current) -> {
            // Recheck after acquiring this call's map lock, including a call that ended meanwhile.
            requireSigner(callId, actor);
            String text = current == null ? "" : current;
            String next = switch (action) {
                case APPEND -> text + letter;
                case SPACE -> text.isEmpty() || text.endsWith(" ") ? text : text + " ";
                case DELETE -> text.isEmpty() ? text : text.substring(0, text.length() - 1);
                case CLEAR -> "";
            };
            AiTextUpdateEvent event = AiTextUpdateEvent.of(callId, call.getConversationId(), next, clock.millis());
            result.set(event);
            send(call.getCallerId(), callId, event);
            if (!call.getReceiverId().equals(call.getCallerId())) send(call.getReceiverId(), callId, event);
            return next;
        });
        return result.get();
    }

    private VideoCall requireSigner(long callId, CurrentUser actor) {
        VideoCall call = calls.validateActiveCall(callId, actor);
        if (actor.userId() != call.getReceiverId()) throw VideoCallException.forbidden();
        return call;
    }

    private void send(long userId, long callId, AiTextUpdateEvent event) {
        try {
            messaging.convertAndSendToUser(Long.toString(userId), "/queue/calls/" + callId, event);
        } catch (RuntimeException ex) {
            log.warn("Could not send AI text event {} to participant {}", event.eventId(), userId, ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCallStatus(VideoCallEvent event) {
        if (VideoCallEvent.CALL_STATUS_CHANGED.equals(event.type())
                && event.status() != VideoCallStatus.ACCEPTED) textByCall.remove(event.callId());
    }

    private enum Action { APPEND, SPACE, DELETE, CLEAR }
}
