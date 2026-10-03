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
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import fptu.exe202.signify.signifybe.features.user.api.UserBlockController;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {ConversationController.class, UserBlockController.class})
public class ChatMultipartExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ChatMultipartExceptionHandler.class);

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> databaseFailure(HttpServletRequest request, DataAccessException exception) {
        log.error("Chat database operation failed", exception);
        return ResponseEntity.internalServerError().body(ApiResponse.error(
                500, "Chat operation failed", request.getRequestURI()));
    }
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
