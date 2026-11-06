package fptu.exe202.signify.signifybe.features.call.application;

import fptu.exe202.signify.signifybe.features.call.domain.VideoCallStatus;
import fptu.exe202.signify.signifybe.features.call.infrastructure.persistence.JpaVideoCallRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class CallTimeoutService {
    private final JpaVideoCallRepository calls;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final long ringingTimeoutMs;
    private final long maxDurationMs;

    public CallTimeoutService(JpaVideoCallRepository calls, Clock clock, ApplicationEventPublisher events,
                              @Value("${call.ringing-timeout-ms:60000}") long ringingTimeoutMs,
                              @Value("${call.max-duration-ms:43200000}") long maxDurationMs) {
        this.calls = calls;
        this.clock = clock;
        this.events = events;
        this.ringingTimeoutMs = ringingTimeoutMs;
        this.maxDurationMs = maxDurationMs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean closeIfTimedOut(long callId) {
        var optional = calls.findLockedById(callId);
        if (optional.isEmpty()) return false;
        var call = optional.get();
        long now = clock.millis();
        if (call.getStatus() == VideoCallStatus.CALLING && call.getCreatedAt() <= now - ringingTimeoutMs) {
            call.miss(now);
        } else if (call.getStatus() == VideoCallStatus.ACCEPTED
                && (call.getStartedAt() == null ? call.getCreatedAt() : call.getStartedAt()) <= now - maxDurationMs) {
            call.complete(now);
        } else {
            return false;
        }
        events.publishEvent(VideoCallEvent.statusChanged(call, now));
        return true;
    }
}
