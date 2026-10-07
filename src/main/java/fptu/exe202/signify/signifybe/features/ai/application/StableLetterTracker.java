package fptu.exe202.signify.signifybe.features.ai.application;

import fptu.exe202.signify.signifybe.features.ai.api.dto.AiPredictionResponse;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallEvent;
import fptu.exe202.signify.signifybe.features.call.domain.VideoCallStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Per-call decoder for accepted letters; it stores no text or cross-call state.
 * Three consecutive valid predictions of one candidate accept that letter once.
 * The accepted letter is held until two consecutive no-hand observations release it,
 * or three predictions of a different letter accept a change. A low score or malformed
 * result resets the candidate but cannot release a held letter. There is no cooldown.
 */
@Component
public class StableLetterTracker {
    static final int STABLE_PREDICTIONS = 3;
    static final int RELEASE_FRAMES = 2;
    static final double MIN_CONFIDENCE = 0.75;

    private final ConcurrentHashMap<Long, CallState> calls = new ConcurrentHashMap<>();

    public AiPredictionResponse observe(long callId, AiPredictionResponse prediction) {
        if (prediction == null || !prediction.isValid() || prediction.confidence() < MIN_CONFIDENCE) {
            calls.computeIfPresent(callId, (ignored, state) -> {
                state.resetCandidate();
                return state;
            });
            return null;
        }
        AtomicReference<AiPredictionResponse> accepted = new AtomicReference<>();
        calls.compute(callId, (ignored, current) -> {
            CallState state = current == null ? new CallState() : current;
            accepted.set(state.observe(prediction));
            return state;
        });
        return accepted.get();
    }

    /** Only an explicit no-hand observation counts as release; outages and low scores do not. */
    public void noHand(long callId) {
        calls.computeIfPresent(callId, (ignored, state) -> {
            state.noHand();
            return state;
        });
    }

    public void clear(long callId) {
        calls.remove(callId);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCallStatus(VideoCallEvent event) {
        if (VideoCallEvent.CALL_STATUS_CHANGED.equals(event.type())
                && event.status() != VideoCallStatus.ACCEPTED) clear(event.callId());
    }

    private static final class CallState {
        private String candidate;
        private int candidateCount;
        private String heldLetter;
        private int noHandCount;

        AiPredictionResponse observe(AiPredictionResponse prediction) {
            noHandCount = 0;
            String letter = prediction.letter();
            if (letter.equals(heldLetter)) {
                resetCandidate();
                return null;
            }
            if (letter.equals(candidate)) candidateCount++;
            else {
                candidate = letter;
                candidateCount = 1;
            }
            if (candidateCount < STABLE_PREDICTIONS) return null;
            heldLetter = letter;
            resetCandidate();
            return prediction;
        }

        void noHand() {
            resetCandidate();
            noHandCount = Math.min(noHandCount + 1, RELEASE_FRAMES);
            if (noHandCount == RELEASE_FRAMES) heldLetter = null;
        }

        void resetCandidate() {
            candidate = null;
            candidateCount = 0;
        }
    }
}
