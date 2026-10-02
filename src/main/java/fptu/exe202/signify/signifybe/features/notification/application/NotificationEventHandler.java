package fptu.exe202.signify.signifybe.features.notification.application;

import fptu.exe202.signify.signifybe.features.notification.contract.MissedCallEvent;
import fptu.exe202.signify.signifybe.features.notification.contract.SubscriptionExpiredEvent;
import fptu.exe202.signify.signifybe.features.notification.contract.SubscriptionExpiringEvent;
import fptu.exe202.signify.signifybe.features.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationEventHandler {
    private final NotificationService notifications;

    @EventListener
    public void on(MissedCallEvent event) {
        String caller = event.callerDisplayName() == null || event.callerDisplayName().isBlank()
                ? "Someone" : event.callerDisplayName().strip();
        notifications.create(event.receiverId(), NotificationType.MISSED_CALL,
                "Missed call", "You missed a call from " + caller,
                "VIDEO_CALL", event.callId(), "missed-call:" + event.callId());
    }

    @EventListener
    public void on(SubscriptionExpiringEvent event) {
        notifications.create(event.userId(), NotificationType.SUBSCRIPTION_EXPIRING,
                "Subscription expiring soon",
                "Your subscription expires in " + event.daysRemaining() + " day(s)",
                "SUBSCRIPTION", event.subscriptionId(),
                "subscription-expiring:" + event.subscriptionId() + ":" + event.expiresAt() + ":" + event.daysRemaining());
    }

    @EventListener
    public void on(SubscriptionExpiredEvent event) {
        notifications.create(event.userId(), NotificationType.SUBSCRIPTION_EXPIRED,
                "Subscription expired", "Your subscription has expired",
                "SUBSCRIPTION", event.subscriptionId(),
                "subscription-expired:" + event.subscriptionId() + ":" + event.expiresAt());
    }
}
