package fptu.exe202.signify.signifybe.features.payment.application;

import fptu.exe202.signify.signifybe.features.payment.domain.PaymentOrder;
import fptu.exe202.signify.signifybe.features.payment.domain.PaymentOrderStatus;
import fptu.exe202.signify.signifybe.features.payment.domain.SubscriptionPlan;
import fptu.exe202.signify.signifybe.features.payment.domain.SubscriptionStatus;
import fptu.exe202.signify.signifybe.features.payment.domain.UserSubscription;
import fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence.JpaPaymentOrderRepository;
import fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence.JpaUserSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {
    private static final long NOW = Instant.parse("2025-01-31T12:00:00Z").toEpochMilli();
    @Mock JpaUserSubscriptionRepository subscriptions;
    @Mock JpaPaymentOrderRepository orders;
    @Mock SubscriptionPlan plan;
    @Mock PaymentOrder order;
    SubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(subscriptions, orders, Clock.fixed(Instant.ofEpochMilli(NOW), ZoneId.of("UTC")));
    }

    @Test
    void reportsValidSubscriptionAndExpirationFromActiveLookup() {
        UserSubscription active = mock(UserSubscription.class);
        when(active.getSubscriptionEndAt()).thenReturn(NOW + 10_000);
        when(subscriptions.findActiveSubscription(7, NOW)).thenReturn(Optional.of(active));

        assertTrue(service.hasActiveSubscription(7));
        assertSame(active, service.getCurrentSubscription(7).orElseThrow());
        assertEquals(NOW + 10_000, service.getSubscriptionExpiration(7).orElseThrow());
    }

    @Test
    void reportsNoActiveSubscriptionWhenRepositoryHasNoValidPeriod() {
        when(subscriptions.findActiveSubscription(7, NOW)).thenReturn(Optional.empty());

        assertFalse(service.hasActiveSubscription(7));
        assertTrue(service.getSubscriptionExpiration(7).isEmpty());
    }

    @Test
    void calculatesOneCalendarMonthInBusinessTimezone() {
        SubscriptionPeriod period = service.calculateNextSubscriptionPeriod(null, NOW, 1);
        ZonedDateTime expected = Instant.ofEpochMilli(NOW).atZone(SubscriptionService.BUSINESS_ZONE).plusMonths(1);

        assertEquals(NOW, period.startsAt());
        assertEquals(expected.toInstant().toEpochMilli(), period.expiresAt());
        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), SubscriptionService.BUSINESS_ZONE);
    }

    @Test
    void earlyRenewalStartsAtExistingExpirationAndExtendsItOneMonth() {
        long oldExpiration = Instant.parse("2025-02-20T04:00:00Z").toEpochMilli();
        preparePaidOrder(100, oldExpiration);

        UserSubscription created = service.activateSubscriptionAfterPayment(100);

        assertEquals(oldExpiration, created.getSubscriptionStartAt());
        assertEquals(service.calculateNextSubscriptionPeriod(oldExpiration, NOW, 1).expiresAt(),
                created.getSubscriptionEndAt());
        verify(order).assignUserSubscription(created);
    }

    @Test
    void renewalAfterExpirationStartsAtPaymentConfirmationTime() {
        long expiredAt = NOW - 1;
        preparePaidOrder(101, expiredAt);

        UserSubscription created = service.activateSubscriptionAfterPayment(101);

        assertEquals(NOW, created.getSubscriptionStartAt());
        assertEquals(service.calculateNextSubscriptionPeriod(null, NOW, 1).expiresAt(),
                created.getSubscriptionEndAt());
    }

    @Test
    void repeatedActivationReturnsSubscriptionAlreadyAssociatedWithPaidOrder() {
        UserSubscription existing = mock(UserSubscription.class);
        when(orders.findLockedByOrderCode(102)).thenReturn(Optional.of(order));
        when(order.getStatus()).thenReturn(PaymentOrderStatus.PAID.getValue());
        when(order.getPaidAt()).thenReturn(NOW);
        when(order.getUserSubscription()).thenReturn(existing);

        assertSame(existing, service.activateSubscriptionAfterPayment(102));
        verifyNoInteractions(subscriptions);
    }

    @Test
    void refusesActivationBeforeTrustedPaymentConfirmation() {
        when(orders.findLockedByOrderCode(103)).thenReturn(Optional.of(order));
        when(order.getStatus()).thenReturn(PaymentOrderStatus.PENDING.getValue());

        assertThrows(IllegalStateException.class, () -> service.activateSubscriptionAfterPayment(103));
        verifyNoInteractions(subscriptions);
    }

    private void preparePaidOrder(long orderCode, long priorExpiration) {
        UserSubscription prior = mock(UserSubscription.class);
        when(orders.findLockedByOrderCode(orderCode)).thenReturn(Optional.of(order));
        when(order.getStatus()).thenReturn(PaymentOrderStatus.PAID.getValue());
        when(order.getPaidAt()).thenReturn(NOW);
        when(order.getUserSubscription()).thenReturn(null);
        when(order.getUserId()).thenReturn(7L);
        when(order.getPlan()).thenReturn(plan);
        when(order.getTotalAmount()).thenReturn(49_000L);
        when(plan.isActive()).thenReturn(true);
        when(plan.getMonthlyPriceAmount()).thenReturn(49_000L);
        when(plan.getCurrencyCode()).thenReturn("VND");
        when(plan.getDurationMonths()).thenReturn(1);
        when(subscriptions.findLockedByUserIdAndStatusOrderBySubscriptionEndAtDesc(7, SubscriptionStatus.ACTIVE.getValue()))
                .thenReturn(List.of(prior));
        when(prior.getSubscriptionEndAt()).thenReturn(priorExpiration);
        when(subscriptions.save(any(UserSubscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
