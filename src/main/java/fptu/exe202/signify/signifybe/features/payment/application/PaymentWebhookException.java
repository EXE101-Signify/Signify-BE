package fptu.exe202.signify.signifybe.features.payment.application;

import fptu.exe202.signify.apiresponse.exception.BaseException;
import org.springframework.http.HttpStatus;

public final class PaymentWebhookException extends BaseException {
    private PaymentWebhookException(HttpStatus status, String message) {
        super(status, message);
    }

    public static PaymentWebhookException invalid() {
        return new PaymentWebhookException(HttpStatus.BAD_REQUEST, "Invalid payment webhook");
    }

    public static PaymentWebhookException mismatch() {
        return new PaymentWebhookException(HttpStatus.CONFLICT, "Payment confirmation does not match order");
    }

    public static PaymentWebhookException retry() {
        return new PaymentWebhookException(HttpStatus.SERVICE_UNAVAILABLE, "Payment confirmation pending verification");
    }
}
