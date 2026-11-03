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
import static org.mockito.ArgumentMatchers.eq;
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
        when(subscriptions.findActiveSubscription(7, NOW)).thenReturn(Optional.of(active));
        when(subscriptions.findLatestEntitlementEnd(eq(7L), any(), eq(NOW)))
                .thenReturn(Optional.of(NOW + 10_000));

        assertTrue(service.hasActiveSubscription(7));
        assertSame(active, service.getCurrentSubscription(7).orElseThrow());
        assertEquals(NOW + 10_000, service.getSubscriptionExpiration(7).orElseThrow());
    }

    @Test
    void reportsNoActiveSubscriptionWhenRepositoryHasNoValidPeriod() {
        when(subscriptions.findActiveSubscription(7, NOW)).thenReturn(Optional.empty());
        when(subscriptions.findLatestEntitlementEnd(eq(7L), any(), eq(NOW))).thenReturn(Optional.empty());

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
    void handlesLeapYearAndEndOfMonthInBusinessTimezone() {
        long january31 = ZonedDateTime.of(2024, 1, 31, 19, 0, 0, 0,
                SubscriptionService.BUSINESS_ZONE).toInstant().toEpochMilli();
        long february29 = ZonedDateTime.of(2024, 2, 29, 19, 0, 0, 0,
                SubscriptionService.BUSINESS_ZONE).toInstant().toEpochMilli();
        long march29 = ZonedDateTime.of(2024, 3, 29, 19, 0, 0, 0,
                SubscriptionService.BUSINESS_ZONE).toInstant().toEpochMilli();

        assertEquals(february29, service.calculateNextSubscriptionPeriod(null, january31, 1).expiresAt());
        assertEquals(march29, service.calculateNextSubscriptionPeriod(february29, january31, 1).expiresAt());
    }

    @Test
    void nonLeapJanuary31ExpiresOnFebruary28() {
        long january31 = ZonedDateTime.of(2025, 1, 31, 19, 0, 0, 0,
                SubscriptionService.BUSINESS_ZONE).toInstant().toEpochMilli();
        long february28 = ZonedDateTime.of(2025, 2, 28, 19, 0, 0, 0,
                SubscriptionService.BUSINESS_ZONE).toInstant().toEpochMilli();

        SubscriptionPeriod period = service.calculateNextSubscriptionPeriod(null, january31, 1);

        assertEquals(january31, period.startsAt());
        assertEquals(february28, period.expiresAt());
    }

    @Test
    void subscriptionHistoryReturnsPersistedPeriods() {
        UserSubscription first = mock(UserSubscription.class);
        UserSubscription second = mock(UserSubscription.class);
        when(subscriptions.findByUserIdOrderBySubscriptionStartAtDesc(7L))
                .thenReturn(List.of(second, first));

        assertEquals(List.of(second, first), service.getSubscriptionHistory(7L));
    }

    @Test
    void scheduledRenewalDoesNotGrantAccessBeforeStart() {
        when(subscriptions.findActiveSubscription(7, NOW)).thenReturn(Optional.empty());
        when(subscriptions.findLatestEntitlementEnd(eq(7L), any(), eq(NOW)))
                .thenReturn(Optional.of(NOW + 86_400_000L));

        assertFalse(service.hasActiveSubscription(7));
        assertEquals(NOW + 86_400_000L, service.getSubscriptionExpiration(7).orElseThrow());
    }

    @Test
    void earlyRenewalStartsAtExistingExpirationAndExtendsItOneMonth() {
        long oldExpiration = Instant.parse("2025-02-20T04:00:00Z").toEpochMilli();
        preparePaidOrder(100, oldExpiration);

        UserSubscription created = service.activateSubscriptionAfterPayment(100);

        assertEquals(oldExpiration, created.getSubscriptionStartAt());
        assertEquals(SubscriptionStatus.PENDING.getValue(), created.getStatus());
        assertEquals(service.calculateNextSubscriptionPeriod(oldExpiration, NOW, 1).expiresAt(),
                created.getSubscriptionEndAt());
        verify(order).assignUserSubscription(created);
    }

    @Test
    void secondEarlyRenewalAppendsAfterAlreadyScheduledPeriod() {
        long firstEnd = Instant.parse("2025-02-20T04:00:00Z").toEpochMilli();
        long scheduledEnd = Instant.parse("2025-03-20T04:00:00Z").toEpochMilli();
        preparePaidOrder(104, firstEnd);
        UserSubscription scheduled = mock(UserSubscription.class);
        when(scheduled.getSubscriptionEndAt()).thenReturn(scheduledEnd);
        UserSubscription original = mock(UserSubscription.class);
        when(original.getSubscriptionEndAt()).thenReturn(firstEnd);
        when(subscriptions.findLockedRenewalPeriods(eq(7L), any(), eq(NOW)))
                .thenReturn(List.of(scheduled, original));

        UserSubscription created = service.activateSubscriptionAfterPayment(104);

        assertEquals(scheduledEnd, created.getSubscriptionStartAt());
        assertEquals(SubscriptionStatus.PENDING.getValue(), created.getStatus());
    }

    @Test
    void reconciliationExpiresOldPeriodsAndStartsDuePendingPeriods() {
        service.reconcileStatuses();
        verify(subscriptions).expireEndedPeriods(any(), eq(SubscriptionStatus.EXPIRED.getValue()), eq(NOW));
        verify(subscriptions).activateStartedPeriods(eq(SubscriptionStatus.PENDING.getValue()),
                eq(SubscriptionStatus.ACTIVE.getValue()), eq(NOW));
    }

    @Test
    void renewalAfterExpirationStartsAtPaymentConfirmationTime() {
        long expiredAt = NOW - 1;
        preparePaidOrder(101, expiredAt);

        UserSubscription created = service.activateSubscriptionAfterPayment(101);

        assertEquals(NOW, created.getSubscriptionStartAt());
        assertEquals(SubscriptionStatus.ACTIVE.getValue(), created.getStatus());
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
        lenient().when(subscriptions.findLockedRenewalPeriods(eq(7L), any(), eq(NOW)))
                .thenReturn(List.of(prior));
        lenient().when(prior.getSubscriptionEndAt()).thenReturn(priorExpiration);
        when(subscriptions.save(any(UserSubscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
