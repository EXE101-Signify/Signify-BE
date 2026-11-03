package fptu.exe202.signify.signifybe.features.payment.application;

import fptu.exe202.signify.signifybe.features.payment.api.PayOSWebhookController;
import fptu.exe202.signify.signifybe.features.payment.domain.PaymentWebhookEvent;
import fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence.JpaPaymentOrderRepository;
import fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence.JpaPaymentTransactionRepository;
import fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence.JpaPaymentWebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentWebhookServiceTest {
    @Test void validRegistrationSampleForUnknownOrderIsAcknowledgedWithoutEntitlement() {
        PaymentWebhookVerifier verifier = mock(PaymentWebhookVerifier.class);
        JpaPaymentOrderRepository orders = mock(JpaPaymentOrderRepository.class);
        JpaPaymentTransactionRepository transactions = mock(JpaPaymentTransactionRepository.class);
        JpaPaymentWebhookEventRepository events = mock(JpaPaymentWebhookEventRepository.class);
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        PaymentGateway gateway = mock(PaymentGateway.class);
        Map<String, Object> payload = Map.of("signature", "signed-sample", "data", Map.of("orderCode", 123L));
        when(verifier.verify(payload)).thenReturn(new VerifiedPayOSWebhook(
                123L, 49_000L, "VND", "sample-reference", "sample-link", "00", null, "00", true));
        when(orders.findLockedByOrderCode(123L)).thenReturn(Optional.empty());
        var processor = new PaymentWebhookProcessor(orders, transactions, events, subscriptions, gateway,
                Clock.systemUTC());
        var controller = new PayOSWebhookController(new PaymentWebhookService(verifier, processor));

        var response = controller.receive(payload);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of("success", true), response.getBody());
        verify(verifier).verify(payload);
        verify(events).save(argThat((PaymentWebhookEvent event) -> event.getOrder() == null
                && event.isSignatureValid() && "IGNORED".equals(event.getProcessingStatus())));
        verifyNoInteractions(transactions, subscriptions, gateway);
    }

    @Test void invalidSignatureCannotReachTransactionalProcessor() {
        PaymentWebhookVerifier verifier = mock(PaymentWebhookVerifier.class);
        PaymentWebhookProcessor processor = mock(PaymentWebhookProcessor.class);
        Map<String, Object> payload = Map.of("signature", "invalid");
        when(verifier.verify(payload)).thenThrow(PaymentWebhookException.invalid());

        assertThrows(PaymentWebhookException.class,
                () -> new PaymentWebhookService(verifier, processor).handle(payload));
        verifyNoInteractions(processor);
    }
}
