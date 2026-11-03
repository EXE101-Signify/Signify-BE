package fptu.exe202.signify.signifybe.features.payment.application;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.payment.domain.*;
import fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentOrderServiceTest {
    @Mock JpaPaymentOrderRepository orders;
    @Mock JpaSubscriptionPlanRepository plans;
    @Mock JpaUserSubscriptionRepository subscriptions;
    PaymentOrderService service;
    CurrentUser user = new CurrentUser(7, 1, Role.USER);
    SubscriptionPlan plan;

    @BeforeEach void setUp() {
        service = new PaymentOrderService(orders, plans, subscriptions,
                Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC));
        plan = mock(SubscriptionPlan.class);
        lenient().when(plan.getMonthlyPriceAmount()).thenReturn(49_000L);
        lenient().when(plan.getCurrencyCode()).thenReturn("VND");
        lenient().when(plan.getDurationMonths()).thenReturn(1);
        lenient().when(plan.getPlanCode()).thenReturn("PREMIUM_MONTHLY");
    }

    @Test void createsPendingOrderWithDatabasePriceAndUniqueCode() {
        when(orders.findPendingOrders(eq(7L), eq("PENDING"), eq(1_000L), any(Pageable.class))).thenReturn(List.of());
        when(plans.findByPlanCodeAndActiveTrue("PREMIUM_MONTHLY")).thenReturn(Optional.of(plan));
        when(subscriptions.findActiveSubscription(7, 1_000)).thenReturn(Optional.empty());
        when(orders.findByOrderCode(anyLong())).thenReturn(Optional.empty());
        when(orders.save(any(PaymentOrder.class))).thenAnswer(invocation -> {
            PaymentOrder order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 3L);
            return order;
        });

        var result = service.createSubscriptionOrder(user);

        assertEquals(49_000, result.amount());
        assertEquals("VND", result.currency());
        assertEquals("PENDING", result.status());
        assertEquals("NEW_SUBSCRIPTION", result.orderType());
        assertTrue(result.orderCode() >= 100_000_000_000L && result.orderCode() < 1_000_000_000_000L);
    }

    @Test void reusesPendingOrder() {
        PaymentOrder pending = new PaymentOrder(100_000_000_001L, 7, plan, "NEW_SUBSCRIPTION", 1_000);
        ReflectionTestUtils.setField(pending, "id", 4L);
        when(orders.findPendingOrders(eq(7L), eq("PENDING"), eq(1_000L), any(Pageable.class)))
                .thenReturn(List.of(pending));

        assertEquals(4, service.createSubscriptionOrder(user).id());
        verify(orders, never()).save(any());
    }

    @Test void rejectsWrongDatabaseAmount() {
        when(plan.getMonthlyPriceAmount()).thenReturn(50_000L);
        when(orders.findPendingOrders(eq(7L), eq("PENDING"), eq(1_000L), any(Pageable.class))).thenReturn(List.of());
        when(plans.findByPlanCodeAndActiveTrue("PREMIUM_MONTHLY")).thenReturn(Optional.of(plan));

        assertThrows(PaymentOrderException.class, () -> service.createSubscriptionOrder(user));
        verify(orders, never()).save(any());
    }

    @Test void rejectsInvalidStateTransitions() {
        PaymentOrder order = new PaymentOrder(100_000_000_002L, 7, plan, "NEW_SUBSCRIPTION", 1_000);
        order.markPaid(2_000);
        assertThrows(PaymentOrderException.class, () -> order.markCancelled(3_000));
        assertThrows(PaymentOrderException.class, () -> order.markPaid(3_000));
    }

    @Test void refusesAnotherUsersOrder() {
        PaymentOrder order = new PaymentOrder(100_000_000_003L, 8, plan, "NEW_SUBSCRIPTION", 1_000);
        when(orders.findById(5L)).thenReturn(Optional.of(order));

        assertThrows(PaymentOrderException.class, () -> service.getPaymentOrderById(5, user));
    }

}
