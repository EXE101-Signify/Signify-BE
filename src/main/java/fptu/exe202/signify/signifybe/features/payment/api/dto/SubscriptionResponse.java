package fptu.exe202.signify.signifybe.features.payment.api.dto;

import fptu.exe202.signify.signifybe.features.payment.domain.UserSubscription;

public record SubscriptionResponse(String planCode, String planName, String subscriptionStatus,
                                   Long subscriptionStartAt, Long subscriptionEndAt, boolean isPremiumActive) {
    public static SubscriptionResponse empty() {
        return new SubscriptionResponse(null, null, null, null, null, false);
    }

    public static SubscriptionResponse from(UserSubscription subscription, long now) {
        boolean active = subscription.getActivatedAt() != null
                && subscription.getSubscriptionStartAt() <= now
                && subscription.getSubscriptionEndAt() > now
                && ("ACTIVE".equals(subscription.getStatus()) || "PENDING".equals(subscription.getStatus()));
        return new SubscriptionResponse(subscription.getPlan().getPlanCode(),
                subscription.getPlan().getPlanName(), subscription.getStatus(),
                subscription.getSubscriptionStartAt(), subscription.getSubscriptionEndAt(), active);
    }

    public SubscriptionResponse withSubscriptionEndAt(Long endAt) {
        return new SubscriptionResponse(planCode, planName, subscriptionStatus,
                subscriptionStartAt, endAt, isPremiumActive);
    }
}
