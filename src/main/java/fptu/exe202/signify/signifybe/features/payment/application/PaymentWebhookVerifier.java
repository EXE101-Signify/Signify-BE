package fptu.exe202.signify.signifybe.features.payment.application;

import java.util.Map;

public interface PaymentWebhookVerifier {
    VerifiedPayOSWebhook verify(Map<String, Object> payload);
}
