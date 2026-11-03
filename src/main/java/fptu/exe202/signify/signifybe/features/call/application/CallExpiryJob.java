package fptu.exe202.signify.signifybe.features.call.application;

import fptu.exe202.signify.signifybe.features.call.domain.VideoCallStatus;
import fptu.exe202.signify.signifybe.features.call.infrastructure.persistence.JpaVideoCallRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CallExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(CallExpiryJob.class);
    private final JpaVideoCallRepository calls;
    private final CallExpiryService expiry;

    public CallExpiryJob(JpaVideoCallRepository calls, CallExpiryService expiry) {
        this.calls = calls;
        this.expiry = expiry;
    }

    @Scheduled(fixedDelayString = "${call.entitlement-check-delay-ms:60000}")
    public void closeCallsWithoutPremium() {
        for (Long callId : calls.findIdsByStatus(VideoCallStatus.ACCEPTED)) {
            try { expiry.closeIfExpired(callId); }
            catch (RuntimeException ex) { log.warn("Could not reconcile call {} entitlement", callId, ex); }
        }
    }
}
