package fptu.exe202.signify.signifybe.storage.api;

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

/** Handles only multipart errors missing from the shared API response library. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = StorageController.class)
public class StorageMultipartExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> tooLarge(HttpServletRequest request) {
        return ResponseEntity.status(413).body(ApiResponse.error(
                413, "Image exceeds the configured maximum size", request.getRequestURI()));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ApiResponse<Void>> invalidMultipart(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.error(
                400, "A valid multipart image part named 'file' is required", request.getRequestURI()));
    }
}
