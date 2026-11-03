package fptu.exe202.signify.signifybe.features.payment.application;

import fptu.exe202.signify.signifybe.features.payment.domain.*;
import fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookProcessorTest {
    @Mock JpaPaymentOrderRepository orders;
    @Mock JpaPaymentTransactionRepository transactions;
    @Mock JpaPaymentWebhookEventRepository events;
    @Mock SubscriptionService subscriptions;
    @Mock PaymentGateway gateway;
    PaymentWebhookProcessor processor;
    PaymentOrder order;
    VerifiedPayOSWebhook success = new VerifiedPayOSWebhook(123L, 49_000L, "VND", "ref-1", "link-1", "00", "2026-01-01 00:00:00", "00", true);

    @BeforeEach void setUp() {
        SubscriptionPlan plan = mock(SubscriptionPlan.class);
        lenient().when(plan.getMonthlyPriceAmount()).thenReturn(49_000L);
        lenient().when(plan.getCurrencyCode()).thenReturn("VND");
        order = new PaymentOrder(123, 7, plan, "NEW_SUBSCRIPTION", 100);
        ReflectionTestUtils.setField(order, "id", 11L);
        processor = new PaymentWebhookProcessor(orders, transactions, events, subscriptions, gateway,
                Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC));
    }

    @Test void validPaymentRecordsTransactionAndActivatesOnce() {
        when(orders.findLockedByOrderCode(123)).thenReturn(Optional.of(order));
        when(gateway.get(123)).thenReturn(paidLink());
        processor.process(success);
        assertEquals("PAID", order.getStatus());
        verify(transactions).save(any(PaymentTransaction.class));
        verify(subscriptions).activateSubscriptionAfterPayment(123);
        verify(events).save(argThat(e -> "PROCESSED".equals(e.getProcessingStatus())));
    }

    @Test void wrongAmountCannotActivate() {
        when(orders.findLockedByOrderCode(123)).thenReturn(Optional.of(order));
        var wrong = new VerifiedPayOSWebhook(123, 1, "VND", "ref-1", "link-1", "00", null, "00", true);
        assertThrows(PaymentWebhookException.class, () -> processor.process(wrong));
        verifyNoInteractions(subscriptions, transactions);
    }

    @Test void wrongCurrencyCannotActivate() {
        when(orders.findLockedByOrderCode(123L)).thenReturn(Optional.of(order));
        var wrong = new VerifiedPayOSWebhook(123L, 49_000L, "USD", "ref-1", "link-1",
                "00", null, "00", true);

        assertThrows(PaymentWebhookException.class, () -> processor.process(wrong));
        verifyNoInteractions(subscriptions, transactions, gateway);
    }

    @Test void providerMustConfirmPaidBeforeActivation() {
        when(orders.findLockedByOrderCode(123L)).thenReturn(Optional.of(order));
        when(gateway.get(123L)).thenReturn(new PaymentGateway.GatewayPaymentLink(
                123L, 49_000L, "link-1", null, "PENDING", null));

        assertThrows(PaymentWebhookException.class, () -> processor.process(success));
        assertEquals("PENDING", order.getStatus());
        verifyNoInteractions(subscriptions, transactions);
        verify(events, never()).save(any());
    }

    @Test void duplicateTransactionReferenceCannotActivateAnotherMonth() {
        when(orders.findLockedByOrderCode(123L)).thenReturn(Optional.of(order));
        PaymentTransaction previous = mock(PaymentTransaction.class);
        when(previous.getOrder()).thenReturn(order);
        when(transactions.findByPaymentProviderAndProviderTransactionReference("PAYOS", "ref-1"))
                .thenReturn(Optional.of(previous));

        processor.process(success);

        verify(transactions, never()).save(any());
        verifyNoInteractions(subscriptions, gateway);
        verify(events).save(argThat(e -> "IGNORED".equals(e.getProcessingStatus())));
    }

    @Test void unknownOrderCannotActivate() {
        when(orders.findLockedByOrderCode(123)).thenReturn(Optional.empty());
        assertThrows(PaymentOrderException.class, () -> processor.process(success));
        verifyNoInteractions(subscriptions, transactions);
    }

    @Test void duplicateEventDoesNotActivate() {
        when(orders.findLockedByOrderCode(123)).thenReturn(Optional.of(order));
        when(events.findByPaymentProviderAndProviderEventKey(eq("PAYOS"), anyString()))
                .thenReturn(Optional.of(new PaymentWebhookEvent()));
        processor.process(success);
        verifyNoInteractions(subscriptions, transactions, gateway);
    }

    @Test void alreadyPaidOrderDoesNotActivateAgain() {
        order.markPaid(500);
        order.assignUserSubscription(mock(UserSubscription.class));
        when(orders.findLockedByOrderCode(123)).thenReturn(Optional.of(order));
        processor.process(success);
        verifyNoInteractions(subscriptions, transactions, gateway);
        verify(events).save(argThat(e -> "IGNORED".equals(e.getProcessingStatus())));
    }

    @Test void failedEventDoesNotActivate() {
        when(orders.findLockedByOrderCode(123)).thenReturn(Optional.of(order));
        var failed = new VerifiedPayOSWebhook(123, 49_000, "VND", "ref-1", "link-1", "01", null, "00", true);
        processor.process(failed);
        assertEquals("PENDING", order.getStatus());
        verifyNoInteractions(subscriptions, transactions, gateway);
        verify(events).save(argThat(e -> "IGNORED".equals(e.getProcessingStatus())));
    }

    @Test void activationFailurePropagatesToTransactionalCaller() {
        when(orders.findLockedByOrderCode(123)).thenReturn(Optional.of(order));
        when(gateway.get(123)).thenReturn(paidLink());
        when(subscriptions.activateSubscriptionAfterPayment(123)).thenThrow(new IllegalStateException("database failure"));
        assertThrows(IllegalStateException.class, () -> processor.process(success));
        verify(events, never()).save(any());
    }

    @Test void duplicateCallbacksSerializedInTestActivateOnce() throws Exception {
        when(orders.findLockedByOrderCode(123)).thenReturn(Optional.of(order));
        when(gateway.get(123)).thenReturn(paidLink());
        AtomicReference<PaymentWebhookEvent> saved = new AtomicReference<>();
        when(events.findByPaymentProviderAndProviderEventKey(eq("PAYOS"), anyString()))
                .thenAnswer(inv -> Optional.ofNullable(saved.get()));
        when(events.save(any(PaymentWebhookEvent.class))).thenAnswer(inv -> {
            PaymentWebhookEvent event = inv.getArgument(0);
            saved.set(event);
            return event;
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> task = () -> { synchronized (order) { processor.process(success); } return null; };
            Future<Void> first = pool.submit(task);
            Future<Void> second = pool.submit(task);
            first.get(); second.get();
            verify(subscriptions, times(1)).activateSubscriptionAfterPayment(123);
        } finally { pool.shutdownNow(); }
    }

    private PaymentGateway.GatewayPaymentLink paidLink() {
        return new PaymentGateway.GatewayPaymentLink(123, 49_000, "link-1", null, "PAID", null);
    }
}
