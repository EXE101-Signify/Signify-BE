package fptu.exe202.signify.signifybe.features.payment.infrastructure;

import fptu.exe202.signify.signifybe.features.payment.application.*;
import org.springframework.stereotype.Component;
import vn.payos.PayOS;
import vn.payos.exception.PayOSException;
import vn.payos.model.webhooks.WebhookData;
import java.util.Map;

@Component
public class PayOSWebhookVerifier implements PaymentWebhookVerifier {
    private final PayOS payOS;
    public PayOSWebhookVerifier(PayOS payOS) { this.payOS = payOS; }

    @Override public VerifiedPayOSWebhook verify(Map<String, Object> payload) {
        if (payload == null || !(payload.get("signature") instanceof String signature) || signature.isBlank()
                || !(payload.get("data") instanceof Map<?, ?>)) throw PaymentWebhookException.invalid();
        try {
            WebhookData data = payOS.webhooks().verify(payload);
            if (data == null || data.getOrderCode() == null || data.getAmount() == null
                    || data.getOrderCode() <= 0 || data.getAmount() <= 0) throw PaymentWebhookException.invalid();
            return new VerifiedPayOSWebhook(data.getOrderCode(), data.getAmount(), data.getCurrency(),
                    data.getReference(), data.getPaymentLinkId(), data.getCode(), data.getTransactionDateTime(),
                    payload.get("code") instanceof String code ? code : null,
                    Boolean.TRUE.equals(payload.get("success")));
        } catch (PayOSException ex) {
            throw PaymentWebhookException.invalid();
        }
    }
}
