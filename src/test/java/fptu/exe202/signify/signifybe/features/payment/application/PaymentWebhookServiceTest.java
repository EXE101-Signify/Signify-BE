package fptu.exe202.signify.signifybe.features.payment.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fptu.exe202.signify.signifybe.features.payment.api.PayOSWebhookController;
import fptu.exe202.signify.signifybe.features.payment.domain.PaymentWebhookEvent;
import fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence.JpaPaymentOrderRepository;
import fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence.JpaPaymentTransactionRepository;
import fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence.JpaPaymentWebhookEventRepository;
import fptu.exe202.signify.signifybe.features.payment.infrastructure.PayOSWebhookVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import vn.payos.PayOS;
import vn.payos.model.webhooks.WebhookData;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentWebhookServiceTest {
    @Test void signedRegistrationSampleWithUnknownOrderIsAcknowledgedWithoutPayment() {
        PayOS sdk = new PayOS("local-client", "local-api-key", "local-webhook-test-key");
        Map<String, Object> payload = signedSample(sdk);
        JpaPaymentOrderRepository orders = mock(JpaPaymentOrderRepository.class);
        JpaPaymentTransactionRepository transactions = mock(JpaPaymentTransactionRepository.class);
        JpaPaymentWebhookEventRepository events = mock(JpaPaymentWebhookEventRepository.class);
        SubscriptionService subscriptions = mock(SubscriptionService.class);
        PaymentGateway gateway = mock(PaymentGateway.class);
        when(orders.findLockedByOrderCode(123L)).thenReturn(Optional.empty());
        var processor = new PaymentWebhookProcessor(orders, transactions, events, subscriptions, gateway,
                Clock.systemUTC());
        var controller = new PayOSWebhookController(new PaymentWebhookService(new PayOSWebhookVerifier(sdk), processor));

        assertEquals(HttpStatus.OK, controller.receive(payload).getStatusCode());
        verify(events).save(argThat((PaymentWebhookEvent event) -> event.getOrder() == null
                && "IGNORED".equals(event.getProcessingStatus()) && event.isSignatureValid()));
        verifyNoInteractions(transactions, subscriptions, gateway);
        verify(orders, never()).save(any());
    }

    @Test void invalidSignedSampleCannotReachOrderLookup() {
        PayOS sdk = new PayOS("local-client", "local-api-key", "local-webhook-test-key");
        Map<String, Object> payload = signedSample(sdk);
        payload.put("signature", "0".repeat(64));
        PaymentWebhookProcessor processor = mock(PaymentWebhookProcessor.class);

        assertThrows(PaymentWebhookException.class,
                () -> new PaymentWebhookService(new PayOSWebhookVerifier(sdk), processor).handle(payload));
        verifyNoInteractions(processor);
    }

    private static Map<String, Object> signedSample(PayOS sdk) {
        WebhookData data = new WebhookData();
        data.setOrderCode(123L);
        data.setAmount(3_000L);
        data.setDescription("VQRIO123");
        data.setAccountNumber("12345678");
        data.setReference("TF230204212323");
        data.setTransactionDateTime("2023-02-04 18:25:00");
        data.setCurrency("VND");
        data.setPaymentLinkId("124c33293c43417ab7879e14c8d9eb18");
        data.setCode("00");
        data.setDesc("Success");
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", "00");
        payload.put("desc", "success");
        payload.put("success", true);
        payload.put("data", new ObjectMapper().convertValue(data, new TypeReference<Map<String, Object>>() {}));
        payload.put("signature", sdk.getCrypto().createSignatureFromObj(data, "local-webhook-test-key"));
        return payload;
    }

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
