package fptu.exe202.signify.signifybe.features.user.domain.exception;

import fptu.exe202.signify.apiresponse.exception.BaseException;
import org.springframework.http.HttpStatus;

public final class BlockException extends BaseException {
    private BlockException(HttpStatus status, String message) { super(status, message); }

    public static BlockException selfBlock() {
        return new BlockException(HttpStatus.BAD_REQUEST, "You cannot block yourself");
    }

    public static BlockException userNotFound() {
        return new BlockException(HttpStatus.NOT_FOUND, "User not found");
    }

    /** Deliberately does not identify which participant initiated the block. */
    public static BlockException interactionUnavailable() {
        return new BlockException(HttpStatus.FORBIDDEN, "Direct interaction is unavailable");
    }
}
