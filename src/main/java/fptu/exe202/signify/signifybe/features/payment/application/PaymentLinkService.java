package fptu.exe202.signify.signifybe.features.payment.application;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import fptu.exe202.signify.signifybe.features.payment.api.dto.PaymentLinkResponse;
import fptu.exe202.signify.signifybe.features.payment.domain.PaymentOrder;
import fptu.exe202.signify.signifybe.features.payment.domain.PaymentOrderException;
import fptu.exe202.signify.signifybe.features.payment.domain.PaymentOrderStatus;
import fptu.exe202.signify.signifybe.features.payment.infrastructure.persistence.JpaPaymentOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class PaymentLinkService {
    private final JpaPaymentOrderRepository orders;
    private final PaymentGateway gateway;
    private final Clock clock;

    public PaymentLinkService(JpaPaymentOrderRepository orders, PaymentGateway gateway, Clock clock) {
        this.orders = orders;
        this.gateway = gateway;
        this.clock = clock;
    }

    @Transactional
    public PaymentLinkResponse createPaymentLink(long orderId, CurrentUser actor) {
        PaymentOrder order = orders.findLockedById(orderId).orElseThrow(PaymentOrderException::notFound);
        requireOwner(order, actor);
        if (!PaymentOrderStatus.PENDING.getValue().equals(order.getStatus()))
            throw PaymentOrderException.invalidTransition();
        if (order.getProviderPaymentLinkId() != null && order.getCheckoutUrl() != null)
            return toResponse(order, null);

        // A prior create request may have succeeded at PayOS before this service timed out.
        PaymentGateway.GatewayPaymentLink existing = gateway.get(order.getOrderCode());
        if (existing != null) {
            validate(order, existing);
            throw PaymentGatewayException.invalidResponse();
        }
        PaymentGateway.GatewayPaymentLink created = gateway.create(order.getOrderCode(), order.getTotalAmount());
        validate(order, created);
        if (created.checkoutUrl() == null || created.checkoutUrl().isBlank())
            throw PaymentGatewayException.invalidResponse();
        order.attachPaymentLink(created.paymentLinkId(), created.checkoutUrl(), created.expiresAt(), clock.millis());
        return toResponse(order, created.status());
    }

    @Transactional(readOnly = true)
    public PaymentLinkResponse getPaymentInformation(long orderId, CurrentUser actor) {
        PaymentOrder order = orders.findById(orderId).orElseThrow(PaymentOrderException::notFound);
        requireOwner(order, actor);
        PaymentGateway.GatewayPaymentLink link = gateway.get(order.getOrderCode());
        if (link == null) throw PaymentGatewayException.invalidResponse();
        validate(order, link);
        return new PaymentLinkResponse(order.getOrderCode(), order.getTotalAmount(), order.getCurrencyCode(),
                link.paymentLinkId(), order.getCheckoutUrl(), link.status(), order.getOrderExpiresAt());
    }

    private static void validate(PaymentOrder order, PaymentGateway.GatewayPaymentLink link) {
        if (link.orderCode() != order.getOrderCode() || link.amount() != order.getTotalAmount()
                || !"VND".equals(order.getCurrencyCode()) || link.paymentLinkId() == null
                || link.paymentLinkId().isBlank()
                || (order.getProviderPaymentLinkId() != null
                && !order.getProviderPaymentLinkId().equals(link.paymentLinkId())))
            throw PaymentGatewayException.invalidResponse();
    }

    private static PaymentLinkResponse toResponse(PaymentOrder order, String status) {
        return new PaymentLinkResponse(order.getOrderCode(), order.getTotalAmount(), order.getCurrencyCode(),
                order.getProviderPaymentLinkId(), order.getCheckoutUrl(), status, order.getOrderExpiresAt());
    }

    private static void requireOwner(PaymentOrder order, CurrentUser actor) {
        if (actor == null) throw AuthException.invalidAccessToken();
        if (order.getUserId() != actor.userId()) throw PaymentOrderException.forbidden();
    }
}
