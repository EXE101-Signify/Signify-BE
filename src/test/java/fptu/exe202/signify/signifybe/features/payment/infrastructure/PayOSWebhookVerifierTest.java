package fptu.exe202.signify.signifybe.features.payment.infrastructure;

import fptu.exe202.signify.signifybe.features.payment.application.PaymentWebhookException;
import org.junit.jupiter.api.Test;
import vn.payos.PayOS;
import vn.payos.exception.PayOSException;
import vn.payos.service.blocking.webhooks.WebhooksService;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayOSWebhookVerifierTest {
    @Test void invalidSignatureNeverProducesVerifiedEvent() {
        PayOS sdk = mock(PayOS.class);
        WebhooksService webhooks = mock(WebhooksService.class);
        when(sdk.webhooks()).thenReturn(webhooks);
        Map<String, Object> payload = Map.of("signature", "invalid", "data", Map.of("orderCode", 123));
        when(webhooks.verify(payload)).thenThrow(new PayOSException("Invalid signature"));

        assertThrows(PaymentWebhookException.class, () -> new PayOSWebhookVerifier(sdk).verify(payload));
    }
}
