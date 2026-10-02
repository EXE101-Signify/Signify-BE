package fptu.exe202.signify.signifybe.features.notification.contract;

public record SubscriptionExpiredEvent(long subscriptionId, long userId, long expiresAt) { }
