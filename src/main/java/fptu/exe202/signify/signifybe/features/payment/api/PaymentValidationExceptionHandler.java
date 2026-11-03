package fptu.exe202.signify.signifybe.features.payment.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import lombok.extern.slf4j.Slf4j;

@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
@RestControllerAdvice(assignableTypes = {PaymentController.class, PayOSWebhookController.class})
public class PaymentValidationExceptionHandler {
    @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> invalidParameters(HttpServletRequest request) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(ApiResponse.error(400, "Invalid payment order ID or pagination", request.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> malformedWebhook(HttpServletRequest request) {
        boolean webhook = "/api/payments/webhooks/payos".equals(request.getRequestURI());
        if (webhook)
            log.warn("PayOS webhook rejected stage=deserialization exception=HttpMessageNotReadableException");
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(ApiResponse.error(400, webhook ? "Invalid payment webhook" : "Invalid payment request",
                        request.getRequestURI()));
    }
}
