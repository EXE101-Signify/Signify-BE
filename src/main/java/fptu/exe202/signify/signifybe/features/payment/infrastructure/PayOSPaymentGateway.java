package fptu.exe202.signify.signifybe.features.payment.infrastructure;

import fptu.exe202.signify.signifybe.features.payment.application.PaymentGateway;
import fptu.exe202.signify.signifybe.features.payment.application.PaymentGatewayException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.payos.PayOS;
import vn.payos.exception.ConnectionTimeoutException;
import vn.payos.exception.NotFoundException;
import vn.payos.exception.PayOSException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;

@Component
public class PayOSPaymentGateway implements PaymentGateway {
    private final PayOS payOS;
    private final String returnUrl;
    private final String cancelUrl;

    public PayOSPaymentGateway(
            PayOS payOS,
            @Value("${spring.payos.return-url}") String returnUrl,
            @Value("${spring.payos.cancel-url}") String cancelUrl
    ) {
        this.payOS = payOS;
        this.returnUrl = returnUrl;
        this.cancelUrl = cancelUrl;
    }

    @Override
    public GatewayPaymentLink create(long orderCode, long amount) {
        try {
            CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                    .orderCode(orderCode).amount(amount).description("Signify Premium")
                    .returnUrl(returnUrl).cancelUrl(cancelUrl).build();
            CreatePaymentLinkResponse response = payOS.paymentRequests().create(request);
            if (response == null || response.getOrderCode() == null || response.getAmount() == null
                    || response.getPaymentLinkId() == null || response.getCheckoutUrl() == null)
                throw PaymentGatewayException.invalidResponse();
            return new GatewayPaymentLink(response.getOrderCode(), response.getAmount(),
                    response.getPaymentLinkId(), response.getCheckoutUrl(),
                    response.getStatus() == null ? null : response.getStatus().name(), response.getExpiredAt());
        } catch (ConnectionTimeoutException ex) {
            throw PaymentGatewayException.timeout();
        } catch (PayOSException ex) {
            throw PaymentGatewayException.unavailable();
        }
    }

    @Override
    public GatewayPaymentLink get(long orderCode) {
        try {
            PaymentLink response = payOS.paymentRequests().get(orderCode);
            if (response == null || response.getOrderCode() == null || response.getAmount() == null
                    || response.getId() == null) throw PaymentGatewayException.invalidResponse();
            return new GatewayPaymentLink(response.getOrderCode(), response.getAmount(), response.getId(),
                    null, response.getStatus() == null ? null : response.getStatus().name(), null);
        } catch (NotFoundException ex) {
            return null;
        } catch (ConnectionTimeoutException ex) {
            throw PaymentGatewayException.timeout();
        } catch (PayOSException ex) {
            throw PaymentGatewayException.unavailable();
        }
    }
}
