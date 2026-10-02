package fptu.exe202.signify.signifybe.features.notification.application;

import fptu.exe202.signify.signifybe.features.notification.contract.MissedCallEvent;
import fptu.exe202.signify.signifybe.features.notification.contract.SubscriptionExpiredEvent;
import fptu.exe202.signify.signifybe.features.notification.contract.SubscriptionExpiringEvent;
import fptu.exe202.signify.signifybe.features.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventHandlerTest {
    @Mock NotificationService notifications;

    @Test
    void missedCallMapsToReceiverNotification() {
        new NotificationEventHandler(notifications).on(new MissedCallEvent(3L, 8L, 7L, "Minh"));

        verify(notifications).create(8L, NotificationType.MISSED_CALL, "Missed call",
                "You missed a call from Minh", "VIDEO_CALL", 3L, "missed-call:3");
    }

    @Test
    void subscriptionEventsUseStableDeduplicationKeys() {
        NotificationEventHandler handler = new NotificationEventHandler(notifications);

        handler.on(new SubscriptionExpiringEvent(9L, 8L, 10_000L, 3));
        handler.on(new SubscriptionExpiredEvent(9L, 8L, 10_000L));

        verify(notifications).create(8L, NotificationType.SUBSCRIPTION_EXPIRING,
                "Subscription expiring soon", "Your subscription expires in 3 day(s)",
                "SUBSCRIPTION", 9L, "subscription-expiring:9:10000:3");
        verify(notifications).create(8L, NotificationType.SUBSCRIPTION_EXPIRED,
                "Subscription expired", "Your subscription has expired",
                "SUBSCRIPTION", 9L, "subscription-expired:9:10000");
    }
}
