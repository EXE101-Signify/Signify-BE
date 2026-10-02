package fptu.exe202.signify.signifybe.features.chat.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ConversationController.class)
public class ChatMultipartExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> tooLarge(HttpServletRequest request) {
        return ResponseEntity.status(413).body(ApiResponse.error(
                413, "Attachment exceeds the configured maximum size", request.getRequestURI()));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ApiResponse<Void>> invalidMultipart(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.error(
                400, "A valid multipart attachment part named 'file' is required", request.getRequestURI()));
    }
}
