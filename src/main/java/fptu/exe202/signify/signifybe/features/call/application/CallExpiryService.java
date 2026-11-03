package fptu.exe202.signify.signifybe.features.call.application;

import fptu.exe202.signify.signifybe.features.call.domain.VideoCallStatus;
import fptu.exe202.signify.signifybe.features.call.infrastructure.persistence.JpaVideoCallRepository;
import fptu.exe202.signify.signifybe.features.payment.application.SubscriptionService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

@Service
public class CallExpiryService {
    private final JpaVideoCallRepository calls;
    private final SubscriptionService subscriptions;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public CallExpiryService(JpaVideoCallRepository calls, SubscriptionService subscriptions,
                             Clock clock, ApplicationEventPublisher events) {
        this.calls = calls;
        this.subscriptions = subscriptions;
        this.clock = clock;
        this.events = events;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean closeIfExpired(long callId) {
        var optional = calls.findLockedById(callId);
        if (optional.isEmpty()) return false;
        var call = optional.get();
        if (call.getStatus() != VideoCallStatus.ACCEPTED) return false;
        if (subscriptions.hasActiveSubscription(call.getCallerId())
                && subscriptions.hasActiveSubscription(call.getReceiverId())) return false;
        long now = clock.millis();
        call.complete(now);
        events.publishEvent(VideoCallEvent.statusChanged(call, now));
        return true;
    }
}
