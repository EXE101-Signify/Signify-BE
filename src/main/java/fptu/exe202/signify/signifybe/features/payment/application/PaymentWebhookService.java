package fptu.exe202.signify.signifybe.features.payment.application;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentWebhookService {

    PaymentWebhookVerifier verifier;

    PaymentWebhookProcessor processor;

    public void handle(Map<String, Object> payload) {
        VerifiedPayOSWebhook verified = verifier.verify(payload);
        try {
            processor.process(verified);
        } catch (RuntimeException ex) {
            log.warn("PayOS webhook failed stage=payment_processing exception={} orderCode={}",
                    ex.getClass().getSimpleName(), verified.orderCode());
            throw ex;
        }
    }
}
