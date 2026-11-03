package fptu.exe202.signify.signifybe.features.payment.api.dto;

import fptu.exe202.signify.signifybe.features.payment.domain.PaymentOrder;

public record PaymentOrderResponse(long id, long orderCode, String planCode, String orderType,
                                   String status, long amount, String currency, Long expiresAt,
                                   Long paidAt, long createdAt) {
    public static PaymentOrderResponse from(PaymentOrder order) {
        return new PaymentOrderResponse(order.getId(), order.getOrderCode(), order.getPlan().getPlanCode(),
                order.getOrderType(), order.getStatus(), order.getTotalAmount(), order.getCurrencyCode(),
                order.getOrderExpiresAt(), order.getPaidAt(), order.getCreatedAt());
    }
}
