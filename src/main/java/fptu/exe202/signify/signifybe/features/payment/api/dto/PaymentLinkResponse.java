package fptu.exe202.signify.signifybe.features.payment.api.dto;

public record PaymentLinkResponse(long orderCode, long amount, String currency, String paymentLinkId,
                                  String checkoutUrl, String gatewayStatus, Long expiresAt) {
}
