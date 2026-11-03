package fptu.exe202.signify.signifybe.features.payment.api.dto;

import fptu.exe202.signify.signifybe.features.payment.domain.SubscriptionPlan;

public record SubscriptionPlanResponse(String planCode, String planName, long monthlyPriceAmount,
                                       String currencyCode, int durationMonths, boolean unlimitedVideoCalls) {
    public static SubscriptionPlanResponse from(SubscriptionPlan plan) {
        return new SubscriptionPlanResponse(plan.getPlanCode(), plan.getPlanName(),
                plan.getMonthlyPriceAmount(), plan.getCurrencyCode(), plan.getDurationMonths(),
                plan.isUnlimitedVideoCalls());
    }
}
