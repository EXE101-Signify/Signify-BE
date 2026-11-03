package fptu.exe202.signify.signifybe.features.payment.application;

import fptu.exe202.signify.signifybe.features.payment.domain.PaymentOrder;
import fptu.exe202.signify.signifybe.features.payment.domain.PaymentOrderStatus;
import fptu.exe202.signify.signifybe.features.payment.domain.SubscriptionPlan;
import fptu.exe202.signify.signifybe.features.payment.domain.SubscriptionStatus;
import fptu.exe202.signify.signifybe.features.payment.domain.UserSubscription;
import fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence.JpaPaymentOrderRepository;
import fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence.JpaUserSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class SubscriptionService {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JpaUserSubscriptionRepository subscriptions;
    private final JpaPaymentOrderRepository orders;
    private final Clock clock;

    public SubscriptionService(JpaUserSubscriptionRepository subscriptions,
                               JpaPaymentOrderRepository orders, Clock clock) {
        this.subscriptions = subscriptions;
        this.orders = orders;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<UserSubscription> getCurrentSubscription(long userId) {
        return subscriptions.findActiveSubscription(userId, clock.millis());
    }

    @Transactional(readOnly = true)
    public boolean hasActiveSubscription(long userId) {
        return getCurrentSubscription(userId).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<Long> getSubscriptionExpiration(long userId) {
        return getCurrentSubscription(userId).map(UserSubscription::getSubscriptionEndAt);
    }

    /** Called only by the trusted payment confirmation application flow, with a persisted order code. */
    @Transactional
    public UserSubscription activateSubscriptionAfterPayment(long orderCode) {
        PaymentOrder order = orders.findLockedByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Payment order not found"));
        if (!PaymentOrderStatus.PAID.getValue().equals(order.getStatus()) || order.getPaidAt() == null) {
            throw new IllegalStateException("Subscription can only be activated for a paid order");
        }
        if (order.getUserSubscription() != null) return order.getUserSubscription();

        long userId = order.getUserId();
        subscriptions.lockRenewalForUser(userId);
        long confirmedAt = order.getPaidAt();
        SubscriptionPlan plan = order.getPlan();
        if (!plan.isActive() || order.getTotalAmount() == null
                || !order.getTotalAmount().equals(plan.getMonthlyPriceAmount())
                || !"VND".equals(plan.getCurrencyCode()) || plan.getDurationMonths() == null
                || plan.getDurationMonths() < 1) {
            throw new IllegalStateException("Paid order does not match the active subscription plan");
        }

        Optional<Long> latestUnexpiredEnd = subscriptions
                .findLockedByUserIdAndStatusOrderBySubscriptionEndAtDesc(userId, SubscriptionStatus.ACTIVE.getValue())
                .stream()
                .map(UserSubscription::getSubscriptionEndAt)
                .filter(end -> end != null && end > confirmedAt)
                .max(Long::compareTo);
        SubscriptionPeriod period = calculateNextSubscriptionPeriod(
                latestUnexpiredEnd.orElse(null),
                confirmedAt, plan.getDurationMonths());
        UserSubscription subscription = new UserSubscription(userId, plan, SubscriptionStatus.ACTIVE.getValue(),
                order.getTotalAmount(), period.startsAt(), period.expiresAt(), confirmedAt, clock.millis());
        subscription = subscriptions.save(subscription);
        order.assignUserSubscription(subscription);
        orders.save(order);
        return subscription;
    }

    public SubscriptionPeriod calculateNextSubscriptionPeriod(Long currentExpiration, long confirmationTime,
                                                               int durationMonths) {
        if (durationMonths < 1) throw new IllegalArgumentException("Duration must be at least one month");
        long start = currentExpiration != null && currentExpiration > confirmationTime
                ? currentExpiration : confirmationTime;
        ZonedDateTime localStart = Instant.ofEpochMilli(start).atZone(BUSINESS_ZONE);
        long end = localStart.plusMonths(durationMonths).toInstant().toEpochMilli();
        return new SubscriptionPeriod(start, end);
    }

    @Transactional(readOnly = true)
    public List<UserSubscription> getSubscriptionHistory(long userId) {
        return subscriptions.findByUserIdOrderBySubscriptionStartAtDesc(userId);
    }
}
