package fptu.exe202.signify.signifybe.features.payment.application;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentWebhookService {

    PaymentWebhookVerifier verifier;

    PaymentWebhookProcessor processor;

    public void handle(Map<String, Object> payload) {
        processor.process(verifier.verify(payload));
    }
}
