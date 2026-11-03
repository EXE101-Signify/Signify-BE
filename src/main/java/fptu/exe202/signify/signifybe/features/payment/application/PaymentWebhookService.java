package fptu.exe202.signify.signifybe.features.payment.application;

import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class PaymentWebhookService {
    private final PaymentWebhookVerifier verifier;
    private final PaymentWebhookProcessor processor;

    public PaymentWebhookService(PaymentWebhookVerifier verifier, PaymentWebhookProcessor processor) {
        this.verifier = verifier;
        this.processor = processor;
    }

    public void handle(Map<String, Object> payload) {
        processor.process(verifier.verify(payload));
    }
}
