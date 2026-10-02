package fptu.exe202.signify.signifybe.features.notification.contract;

public record SubscriptionExpiringEvent(long subscriptionId, long userId, long expiresAt, int daysRemaining) { }
