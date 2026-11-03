package fptu.exe202.signify.signifybe.features.payment.infrastructure;

import fptu.exe202.signify.signifybe.features.payment.application.PaymentWebhookException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.payos.PayOS;
import vn.payos.exception.WebhookException;
import vn.payos.model.webhooks.WebhookData;
import vn.payos.service.blocking.webhooks.WebhooksService;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayOSWebhookVerifierTest {
    private static final String TEST_CHECKSUM_KEY = "local-webhook-test-key";

    @Test void sdkVerifiesOfficiallyShapedRegistrationSampleWithRuntimeKey() {
        PayOS sdk = new PayOS("local-client", "local-api-key", TEST_CHECKSUM_KEY);
        Map<String, Object> payload = signedPayload(sdk);

        var result = new PayOSWebhookVerifier(sdk).verify(payload);

        assertEquals(123L, result.orderCode());
        assertEquals(3_000L, result.amount());
        assertEquals("VND", result.currency());
        assertEquals("00", result.dataCode());
        assertTrue(result.envelopeSuccess());
    }

    @Test void sdkRejectsIncorrectSignatureAndWrongChecksumKey() {
        PayOS sdk = new PayOS("local-client", "local-api-key", TEST_CHECKSUM_KEY);
        Map<String, Object> invalidSignaturePayload = signedPayload(sdk);
        invalidSignaturePayload.put("signature", "0".repeat(64));
        assertThrows(PaymentWebhookException.class,
                () -> new PayOSWebhookVerifier(sdk).verify(invalidSignaturePayload));

        Map<String, Object> correctlySignedPayload = signedPayload(sdk);
        PayOS wrongKeySdk = new PayOS("local-client", "local-api-key", "different-local-key");
        assertThrows(PaymentWebhookException.class,
                () -> new PayOSWebhookVerifier(wrongKeySdk).verify(correctlySignedPayload));
    }

    @Test void sdkRejectsMalformedWebhookData() {
        PayOS sdk = new PayOS("local-client", "local-api-key", TEST_CHECKSUM_KEY);
        Map<String, Object> payload = Map.of("signature", "a".repeat(64), "code", "00",
                "success", true, "data", Map.of("orderCode", "not-a-number"));
        assertThrows(PaymentWebhookException.class, () -> new PayOSWebhookVerifier(sdk).verify(payload));
    }

    @Test void signedPayloadWithoutSuccessCannotIndicatePayment() {
        PayOS sdk = new PayOS("local-client", "local-api-key", TEST_CHECKSUM_KEY);
        Map<String, Object> payload = signedPayload(sdk);
        payload.remove("success");

        assertFalse(new PayOSWebhookVerifier(sdk).verify(payload).envelopeSuccess());
    }

    @Test void sdkVerifiesRegistrationSampleWithIncompletePaymentFields() {
        PayOS sdk = new PayOS("local-client", "local-api-key", TEST_CHECKSUM_KEY);

        var result = new PayOSWebhookVerifier(sdk).verify(signedPayload(sdk, true));

        assertEquals(123L, result.orderCode());
        assertEquals(0, result.amount());
        assertNull(result.currency());
        assertNull(result.paymentLinkId());
    }

    private static Map<String, Object> signedPayload(PayOS sdk) {
        return signedPayload(sdk, false);
    }

    private static Map<String, Object> signedPayload(PayOS sdk, boolean incomplete) {
        WebhookData data = new WebhookData();
        data.setOrderCode(123L);
        if (!incomplete) data.setAmount(3_000L);
        data.setDescription("VQRIO123");
        data.setAccountNumber("12345678");
        data.setReference("TF230204212323");
        data.setTransactionDateTime("2023-02-04 18:25:00");
        if (!incomplete) {
            data.setCurrency("VND");
            data.setPaymentLinkId("124c33293c43417ab7879e14c8d9eb18");
            data.setCode("00");
        }
        data.setDesc("Success");
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("code", "00");
        payload.put("desc", "success");
        payload.put("success", true);
        Map<String, Object> dataMap = new ObjectMapper().convertValue(data, new TypeReference<Map<String, Object>>() {});
        dataMap.values().removeIf(java.util.Objects::isNull);
        payload.put("data", dataMap);
        payload.put("signature", sdk.getCrypto().createSignatureFromObj(data, TEST_CHECKSUM_KEY));
        return payload;
    }

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
        when(webhooks.verify(payload)).thenThrow(new WebhookException("Invalid signature"));

        assertThrows(PaymentWebhookException.class, () -> new PayOSWebhookVerifier(sdk).verify(payload));
        verify(webhooks).verify(payload);
    }
}
