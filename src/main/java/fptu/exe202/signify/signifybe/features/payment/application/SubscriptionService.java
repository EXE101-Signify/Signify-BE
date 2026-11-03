package fptu.exe202.signify.signifybe.features.payment.application;

import fptu.exe202.signify.signifybe.features.payment.domain.PaymentOrder;
import fptu.exe202.signify.signifybe.features.payment.domain.PaymentOrderStatus;
import fptu.exe202.signify.signifybe.features.payment.domain.SubscriptionPlan;
import fptu.exe202.signify.signifybe.features.payment.domain.SubscriptionStatus;
import fptu.exe202.signify.signifybe.features.payment.domain.UserSubscription;
import fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence.JpaPaymentOrderRepository;
import fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence.JpaUserSubscriptionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SubscriptionService {
    public static ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    JpaUserSubscriptionRepository subscriptions;

    JpaPaymentOrderRepository orders;

    Clock clock;

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
        return subscriptions.findLatestEntitlementEnd(userId, List.of(SubscriptionStatus.ACTIVE.getValue(),
                SubscriptionStatus.PENDING.getValue()), clock.millis());
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
                || plan.getDurationMonths() != 1) {
            throw new IllegalStateException("Paid order does not match the active subscription plan");
        }

        Optional<Long> latestUnexpiredEnd = subscriptions
                .findLockedRenewalPeriods(userId, List.of(SubscriptionStatus.ACTIVE.getValue(),
                        SubscriptionStatus.PENDING.getValue()), confirmedAt)
                .stream()
                .map(UserSubscription::getSubscriptionEndAt)
                .filter(end -> end != null && end > confirmedAt)
                .max(Long::compareTo);
        SubscriptionPeriod period = calculateNextSubscriptionPeriod(
                latestUnexpiredEnd.orElse(null),
                confirmedAt, plan.getDurationMonths());
        String status = period.startsAt() > clock.millis()
                ? SubscriptionStatus.PENDING.getValue() : SubscriptionStatus.ACTIVE.getValue();
        UserSubscription subscription = new UserSubscription(userId, plan, status,
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

    @Scheduled(fixedDelayString = "${subscription.reconcile-delay-ms:300000}")
    @Transactional
    public void reconcileStatuses() {
        long now = clock.millis();
        subscriptions.expireEndedPeriods(List.of(SubscriptionStatus.ACTIVE.getValue(),
                SubscriptionStatus.PENDING.getValue()), SubscriptionStatus.EXPIRED.getValue(), now);
        subscriptions.activateStartedPeriods(SubscriptionStatus.PENDING.getValue(),
                SubscriptionStatus.ACTIVE.getValue(), now);
    }
}
