package fptu.exe202.signify.signifybe.features.payment.infrastructure;

import fptu.exe202.signify.signifybe.features.payment.application.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.payos.PayOS;
import vn.payos.exception.PayOSException;
import vn.payos.exception.WebhookException;
import vn.payos.model.webhooks.WebhookData;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PayOSWebhookVerifier implements PaymentWebhookVerifier {
    PayOS payOS;

    @Override public VerifiedPayOSWebhook verify(Map<String, Object> payload) {
        if (payload == null || !(payload.get("signature") instanceof String signature) || signature.isBlank()
                || !(payload.get("data") instanceof Map<?, ?>)) {
            log.warn("PayOS webhook rejected stage=payload_shape exception=PaymentWebhookException reason=missing_signature_or_data");
            throw PaymentWebhookException.invalid();
        }
        WebhookData data;
        try {
            data = payOS.webhooks().verify(payload);
        } catch (WebhookException ex) {
            String reason = "Invalid signature".equals(ex.getMessage()) || "Data not integrity".equals(ex.getMessage())
                    ? "invalid_signature" : "invalid_payload_mapping";
            log.warn("PayOS webhook rejected stage=sdk_verification exception={} reason={}",
                    ex.getClass().getSimpleName(), reason);
            throw PaymentWebhookException.invalid();
        } catch (PayOSException ex) {
            log.error("PayOS webhook failed stage=sdk_verification exception={} reason=sdk_failure",
                    ex.getClass().getSimpleName());
            throw PaymentWebhookException.retry();
        }
        if (data == null || data.getOrderCode() == null || data.getAmount() == null
                || data.getOrderCode() <= 0 || data.getAmount() <= 0
                || data.getCurrency() == null || data.getCurrency().isBlank()
                || data.getPaymentLinkId() == null || data.getPaymentLinkId().isBlank()
                || data.getCode() == null || data.getCode().isBlank()
                || !(payload.get("code") instanceof String envelopeCode) || envelopeCode.isBlank()
                || !(payload.get("success") instanceof Boolean envelopeSuccess)) {
            log.warn("PayOS webhook rejected stage=verified_data_validation exception=PaymentWebhookException orderCode={}",
                    data == null ? null : data.getOrderCode());
            throw PaymentWebhookException.invalid();
        }
        log.debug("PayOS webhook verified stage=signature_verified orderCode={}", data.getOrderCode());
        return new VerifiedPayOSWebhook(data.getOrderCode(), data.getAmount(), data.getCurrency(),
                data.getReference(), data.getPaymentLinkId(), data.getCode(), data.getTransactionDateTime(),
                envelopeCode, envelopeSuccess);
    }
}
