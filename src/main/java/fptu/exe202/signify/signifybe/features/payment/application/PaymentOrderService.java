package fptu.exe202.signify.signifybe.features.payment.application;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import fptu.exe202.signify.signifybe.features.payment.api.dto.PaymentOrderResponse;
import fptu.exe202.signify.signifybe.features.payment.domain.*;
import fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;

@Service
public class PaymentOrderService {
    private static final String PREMIUM_CODE = "PREMIUM_MONTHLY";
    private static final long MIN_ORDER_CODE = 100_000_000_000L;
    private static final long ORDER_CODE_RANGE = 900_000_000_000L;
    private final JpaPaymentOrderRepository orders;
    private final JpaSubscriptionPlanRepository plans;
    private final JpaUserSubscriptionRepository subscriptions;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PaymentOrderService(JpaPaymentOrderRepository orders, JpaSubscriptionPlanRepository plans,
                               JpaUserSubscriptionRepository subscriptions, Clock clock) {
        this.orders = orders;
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    @Transactional
    public PaymentOrderResponse createSubscriptionOrder(CurrentUser authenticatedUser) {
        long userId = userId(authenticatedUser);
        subscriptions.lockRenewalForUser(userId);
        long now = clock.millis();
        var pending = orders.findPendingOrders(userId, PaymentOrderStatus.PENDING.getValue(), now, PageRequest.of(0, 1));
        if (!pending.isEmpty()) return PaymentOrderResponse.from(pending.get(0));
        SubscriptionPlan plan = plans.findByPlanCodeAndActiveTrue(PREMIUM_CODE)
                .orElseThrow(PaymentOrderException::invalidPlan);
        if (!Long.valueOf(49_000).equals(plan.getMonthlyPriceAmount())
                || !"VND".equals(plan.getCurrencyCode()) || !Integer.valueOf(1).equals(plan.getDurationMonths())) {
            throw PaymentOrderException.invalidPlan();
        }
        String type = subscriptions.findActiveSubscription(userId, now).isPresent()
                ? PaymentOrderType.RENEWAL.getValue() : PaymentOrderType.NEW_SUBSCRIPTION.getValue();
        long code;
        do { code = MIN_ORDER_CODE + random.nextLong(ORDER_CODE_RANGE); }
        while (orders.findByOrderCode(code).isPresent());
        return PaymentOrderResponse.from(orders.save(new PaymentOrder(code, userId, plan, type, now)));
    }

    @Transactional(readOnly = true)
    public PaymentOrderResponse getPaymentOrderById(long id, CurrentUser authenticatedUser) {
        PaymentOrder order = orders.findById(id).orElseThrow(PaymentOrderException::notFound);
        requireOwner(order, authenticatedUser);
        return PaymentOrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<PaymentOrderResponse> getUserPaymentOrders(CurrentUser authenticatedUser, int page, int size) {
        long userId = userId(authenticatedUser);
        if (page < 0 || size < 1 || size > 100) throw PaymentOrderException.invalidPagination();
        return orders.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .stream().map(PaymentOrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PaymentOrderResponse findPaymentOrderByOrderCode(long orderCode, CurrentUser authenticatedUser) {
        PaymentOrder order = orders.findByOrderCode(orderCode).orElseThrow(PaymentOrderException::notFound);
        requireOwner(order, authenticatedUser);
        return PaymentOrderResponse.from(order);
    }

    /** Package scoped: only verified payment processing in this application package may call this. */
    @Transactional
    PaymentOrder markPaymentOrderPaid(long orderCode, long verifiedPaidAt) {
        PaymentOrder order = orders.findLockedByOrderCode(orderCode).orElseThrow(PaymentOrderException::notFound);
        order.markPaid(verifiedPaidAt);
        return order;
    }

    @Transactional
    public void markPaymentOrderExpired(long orderCode) {
        PaymentOrder order = orders.findLockedByOrderCode(orderCode).orElseThrow(PaymentOrderException::notFound);
        order.markExpired(clock.millis());
    }

    @Transactional
    public void markPaymentOrderCancelled(long orderCode, CurrentUser authenticatedUser) {
        PaymentOrder order = orders.findLockedByOrderCode(orderCode).orElseThrow(PaymentOrderException::notFound);
        requireOwner(order, authenticatedUser);
        order.markCancelled(clock.millis());
    }

    private static long userId(CurrentUser user) {
        if (user == null) throw AuthException.invalidAccessToken();
        return user.userId();
    }

    private static void requireOwner(PaymentOrder order, CurrentUser user) {
        if (order.getUserId() != userId(user)) throw PaymentOrderException.forbidden();
    }
}
