package fptu.exe202.signify.signifybe.features.payment.domain;

import fptu.exe202.signify.apiresponse.exception.BaseException;
import org.springframework.http.HttpStatus;

public final class PaymentOrderException extends BaseException {
    private PaymentOrderException(HttpStatus status, String message) { super(status, message); }
    public static PaymentOrderException notFound() { return new PaymentOrderException(HttpStatus.NOT_FOUND, "Payment order not found"); }
    public static PaymentOrderException forbidden() { return new PaymentOrderException(HttpStatus.FORBIDDEN, "Payment order access denied"); }
    public static PaymentOrderException invalidTransition() { return new PaymentOrderException(HttpStatus.CONFLICT, "Invalid payment order state transition"); }
    public static PaymentOrderException invalidPlan() { return new PaymentOrderException(HttpStatus.CONFLICT, "Premium Monthly plan is unavailable or invalid"); }
}
