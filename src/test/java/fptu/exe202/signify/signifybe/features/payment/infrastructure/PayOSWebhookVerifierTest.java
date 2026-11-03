package fptu.exe202.signify.signifybe.features.payment.infrastructure;

import fptu.exe202.signify.signifybe.features.payment.application.PaymentWebhookException;
import org.junit.jupiter.api.Test;
import vn.payos.PayOS;
import vn.payos.exception.PayOSException;
import vn.payos.model.webhooks.WebhookData;
import vn.payos.service.blocking.webhooks.WebhooksService;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayOSWebhookVerifierTest {
    @Test void mapsSdkVerifiedRegistrationSample() {
        PayOS sdk = mock(PayOS.class);
        WebhooksService webhooks = mock(WebhooksService.class);
        when(sdk.webhooks()).thenReturn(webhooks);
        Map<String, Object> payload = Map.of("signature", "signed-sample", "code", "00",
                "success", true, "data", Map.of("orderCode", 123L));
        WebhookData verified = new WebhookData();
        verified.setOrderCode(123L);
        verified.setAmount(49_000L);
        verified.setCurrency("VND");
        verified.setPaymentLinkId("sample-link");
        verified.setCode("00");
        when(webhooks.verify(payload)).thenReturn(verified);

        var result = new PayOSWebhookVerifier(sdk).verify(payload);

        assertEquals(123L, result.orderCode());
        assertEquals(49_000L, result.amount());
        assertEquals("VND", result.currency());
        assertTrue(result.envelopeSuccess());
        verify(webhooks).verify(payload);
    }

    @Test void rejectsMalformedPayloadBeforeCallingSdk() {
        PayOS sdk = mock(PayOS.class);

        assertThrows(PaymentWebhookException.class,
                () -> new PayOSWebhookVerifier(sdk).verify(Map.of("data", Map.of("orderCode", 123))));
        verifyNoInteractions(sdk);
    }

    @Test void invalidSignatureNeverProducesVerifiedEvent() {
        PayOS sdk = mock(PayOS.class);
        WebhooksService webhooks = mock(WebhooksService.class);
        when(sdk.webhooks()).thenReturn(webhooks);
        Map<String, Object> payload = Map.of("signature", "invalid", "code", "00", "success", true,
                "data", Map.of("orderCode", 123));
        when(webhooks.verify(payload)).thenThrow(new PayOSException("Invalid signature"));

        assertThrows(PaymentWebhookException.class, () -> new PayOSWebhookVerifier(sdk).verify(payload));
        verify(webhooks).verify(payload);
    }
}
