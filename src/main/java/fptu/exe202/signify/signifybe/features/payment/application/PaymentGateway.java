package fptu.exe202.signify.signifybe.features.payment.application;

public interface PaymentGateway {
    GatewayPaymentLink create(long orderCode, long amount);

    GatewayPaymentLink get(long orderCode);

    record GatewayPaymentLink(long orderCode, long amount, String paymentLinkId, String checkoutUrl,
                              String status, Long expiresAt) {
    }
}
