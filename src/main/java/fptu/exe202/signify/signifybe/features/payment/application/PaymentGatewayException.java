package fptu.exe202.signify.signifybe.features.payment.application;

import fptu.exe202.signify.apiresponse.exception.BaseException;
import org.springframework.http.HttpStatus;

public final class PaymentGatewayException extends BaseException {
    private PaymentGatewayException(HttpStatus status, String message) {
        super(status, message);
    }

    public static PaymentGatewayException unavailable() {
        return new PaymentGatewayException(HttpStatus.BAD_GATEWAY, "Payment gateway unavailable");
    }

    public static PaymentGatewayException timeout() {
        return new PaymentGatewayException(HttpStatus.GATEWAY_TIMEOUT, "Payment gateway timed out");
    }

    public static PaymentGatewayException invalidResponse() {
        return new PaymentGatewayException(HttpStatus.BAD_GATEWAY, "Invalid payment gateway response");
    }
}
