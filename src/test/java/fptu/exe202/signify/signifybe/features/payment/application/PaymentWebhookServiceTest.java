package fptu.exe202.signify.signifybe.features.payment.application;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PaymentWebhookServiceTest {
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
