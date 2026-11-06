package fptu.exe202.signify.signifybe.features.call.application;

import fptu.exe202.signify.signifybe.features.call.domain.VideoCallStatus;
import fptu.exe202.signify.signifybe.features.call.infrastructure.persistence.JpaVideoCallRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class CallTimeoutJob {
    private static final Logger log = LoggerFactory.getLogger(CallTimeoutJob.class);
    private final JpaVideoCallRepository calls;
    private final CallTimeoutService timeouts;
    private final Clock clock;
    private final long ringingTimeoutMs;
    private final long maxDurationMs;

    public CallTimeoutJob(JpaVideoCallRepository calls, CallTimeoutService timeouts, Clock clock,
                          @Value("${call.ringing-timeout-ms:60000}") long ringingTimeoutMs,
                          @Value("${call.max-duration-ms:43200000}") long maxDurationMs) {
        this.calls = calls;
        this.timeouts = timeouts;
        this.clock = clock;
        this.ringingTimeoutMs = ringingTimeoutMs;
        this.maxDurationMs = maxDurationMs;
    }

    @Scheduled(fixedDelayString = "${call.timeout-sweep-ms:15000}")
    public void closeTimedOutCalls() {
        long now = clock.millis();
        for (Long callId : calls.findRingingBefore(VideoCallStatus.CALLING, now - ringingTimeoutMs))
            close(callId);
        for (Long callId : calls.findConnectedBefore(VideoCallStatus.ACCEPTED, now - maxDurationMs))
            close(callId);
    }

    private void close(long callId) {
        try { timeouts.closeIfTimedOut(callId); }
        catch (RuntimeException ex) { log.warn("Could not reconcile timed out call {}", callId, ex); }
    }
}
