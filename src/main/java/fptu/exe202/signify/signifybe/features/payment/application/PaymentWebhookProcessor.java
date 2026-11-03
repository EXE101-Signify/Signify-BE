package fptu.exe202.signify.signifybe.features.payment.application;

import fptu.exe202.signify.signifybe.features.payment.domain.*;
import fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class PaymentWebhookProcessor {
    static String PROVIDER = "PAYOS";

    JpaPaymentOrderRepository orders;

    JpaPaymentTransactionRepository transactions;

    JpaPaymentWebhookEventRepository events;

    SubscriptionService subscriptions;

    PaymentGateway gateway;

    Clock clock;

    @Transactional
    public void process(VerifiedPayOSWebhook data) {
        PaymentOrder order = orders.findLockedByOrderCode(data.orderCode()).orElse(null);
        if (order != null && (!PROVIDER.equals(order.getPaymentProvider()) || !"VND".equals(data.currency())
                || !order.getCurrencyCode().equals(data.currency()) || order.getOrderCode() != data.orderCode()
                || order.getTotalAmount() != data.amount()
                || (order.getProviderPaymentLinkId() != null
                    && !order.getProviderPaymentLinkId().equals(data.paymentLinkId()))))
            throw PaymentWebhookException.mismatch();

        String key = eventKey(data);
        if (events.findByPaymentProviderAndProviderEventKey(PROVIDER, key).isPresent()) return;
        long now = clock.millis();
        PaymentWebhookEvent event = new PaymentWebhookEvent();
        event.setPaymentProvider(PROVIDER);
        event.setProviderEventKey(key);
        event.setOrder(order);
        event.setSignatureValid(true);
        event.setReceivedAt(now);
        Map<String, Object> safePayload = new LinkedHashMap<>();
        safePayload.put("orderCode", data.orderCode());
        safePayload.put("amount", data.amount());
        safePayload.put("currency", data.currency());
        safePayload.put("dataCode", data.dataCode());
        safePayload.put("envelopeCode", data.envelopeCode());
        safePayload.put("envelopeSuccess", data.envelopeSuccess());
        safePayload.put("reference", data.reference());
        safePayload.put("paymentLinkId", data.paymentLinkId());
        event.setWebhookPayload(safePayload);

        if (order == null) {
            log.info("PayOS webhook ignored stage=order_lookup reason=unknown_order orderCode={}", data.orderCode());
            event.setProcessingStatus(WebhookProcessingStatus.IGNORED.getValue());
            event.setProcessingErrorMessage("Unknown payment order");
            event.setProcessedAt(now);
            events.save(event);
            return;
        }

        boolean success = data.envelopeSuccess() && "00".equals(data.envelopeCode())
                && "00".equals(data.dataCode());
        if (!success) {
            event.setProcessingStatus(WebhookProcessingStatus.IGNORED.getValue());
            event.setProcessedAt(now);
            events.save(event);
            return;
        }
        String reference = data.reference() == null || data.reference().isBlank()
                ? "event:" + key : data.reference().trim();
        if (reference.length() > 255) throw PaymentWebhookException.invalid();
        var previous = transactions.findByPaymentProviderAndProviderTransactionReference(PROVIDER, reference);
        if (previous.isPresent()) {
            if (!previous.get().getOrder().getId().equals(order.getId())) throw PaymentWebhookException.mismatch();
            event.setProcessingStatus(WebhookProcessingStatus.IGNORED.getValue());
            event.setProcessedAt(now);
            events.save(event);
            return;
        }
        if (PaymentOrderStatus.PAID.getValue().equals(order.getStatus())) {
            if (order.getUserSubscription() == null) throw PaymentWebhookException.retry();
            event.setProcessingStatus(WebhookProcessingStatus.IGNORED.getValue());
            event.setProcessedAt(now);
            events.save(event);
            return;
        }
        if (!PaymentOrderStatus.PENDING.getValue().equals(order.getStatus())) {
            event.setProcessingStatus(WebhookProcessingStatus.IGNORED.getValue());
            event.setProcessedAt(now);
            events.save(event);
            return;
        }
        var remote = gateway.get(data.orderCode());
        if (remote == null || !"PAID".equals(remote.status()) || remote.orderCode() != data.orderCode()
                || remote.amount() != data.amount()
                || remote.paymentLinkId() == null
                || !remote.paymentLinkId().equals(data.paymentLinkId())) throw PaymentWebhookException.retry();

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrder(order);
        transaction.setPaymentProvider(PROVIDER);
        transaction.setProviderTransactionReference(reference);
        transaction.setTransactionAmount(data.amount());
        transaction.setCurrencyCode(data.currency());
        transaction.setStatus(PaymentTransactionStatus.COMPLETED.getValue());
        transaction.setCompletedAt(now);
        transaction.setCreatedAt(now);
        transactions.save(transaction);
        order.markPaid(now);
        subscriptions.activateSubscriptionAfterPayment(order.getOrderCode());
        event.setProcessingStatus(WebhookProcessingStatus.PROCESSED.getValue());
        event.setProcessedAt(now);
        events.save(event);
    }

    private static String eventKey(VerifiedPayOSWebhook data) {
        String material = data.orderCode() + "|" + data.amount() + "|" + data.currency() + "|"
                + data.reference() + "|" + data.paymentLinkId() + "|" + data.dataCode() + "|"
                + data.transactionDateTime();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
}
