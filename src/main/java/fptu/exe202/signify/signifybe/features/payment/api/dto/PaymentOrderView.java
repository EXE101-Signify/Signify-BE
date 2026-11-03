package fptu.exe202.signify.signifybe.features.payment.api.dto;

public record PaymentOrderView(long paymentOrderId, long orderCode, long totalAmount, String currencyCode,
                               String orderStatus, String planCode, String orderType, String checkoutUrl,
                               Long orderExpiresAt, Long paidAt, long createdAt) {
    public static PaymentOrderView from(PaymentOrderResponse order) {
        return new PaymentOrderView(order.id(), order.orderCode(), order.amount(), order.currency(),
                order.status(), order.planCode(), order.orderType(), order.checkoutUrl(),
                order.expiresAt(), order.paidAt(), order.createdAt());
    }

    public PaymentOrderView withCheckout(PaymentLinkResponse link) {
        return new PaymentOrderView(paymentOrderId, orderCode, totalAmount, currencyCode,
                orderStatus, planCode, orderType, link.checkoutUrl(), link.expiresAt(), paidAt, createdAt);
    }
}
