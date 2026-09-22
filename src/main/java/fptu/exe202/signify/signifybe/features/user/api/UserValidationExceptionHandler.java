package fptu.exe202.signify.signifybe.features.user.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Spring MVC method validation errors not yet handled by the shared response library. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {AdminUserController.class, UserController.class})
public class UserValidationExceptionHandler {
    @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> invalidParameters(HttpServletRequest request) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(ApiResponse.error(
                400, "Invalid user ID, pagination or profile fields", request.getRequestURI()));
    }
}
