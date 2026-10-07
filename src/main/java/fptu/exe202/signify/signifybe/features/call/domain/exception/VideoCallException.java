package fptu.exe202.signify.signifybe.features.call.domain.exception;

import fptu.exe202.signify.apiresponse.exception.BaseException;
import org.springframework.http.HttpStatus;

public final class VideoCallException extends BaseException {
    private VideoCallException(HttpStatus status, String message) { super(status, message); }

    public static VideoCallException notFound() {
        return new VideoCallException(HttpStatus.NOT_FOUND, "Call not found");
    }

    public static VideoCallException forbidden() {
        return new VideoCallException(HttpStatus.FORBIDDEN, "Call access denied");
    }

    public static VideoCallException invalidTransition() {
        return new VideoCallException(HttpStatus.CONFLICT, "Invalid call state transition");
    }

    public static VideoCallException invalidId() {
        return new VideoCallException(HttpStatus.BAD_REQUEST, "Invalid call ID");
    }

    public static VideoCallException notActive() {
        return new VideoCallException(HttpStatus.CONFLICT, "Call is not active");
    }
}
