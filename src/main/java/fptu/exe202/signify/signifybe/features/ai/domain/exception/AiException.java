package fptu.exe202.signify.signifybe.features.ai.domain.exception;

import fptu.exe202.signify.apiresponse.exception.BaseException;
import org.springframework.http.HttpStatus;

public final class AiException extends BaseException {
    private AiException(HttpStatus status, String message) {
        super(status, message);
    }

    public static AiException unavailable() {
        return new AiException(HttpStatus.BAD_GATEWAY, "AI service unavailable");
    }

    public static AiException timeout() {
        return new AiException(HttpStatus.GATEWAY_TIMEOUT, "AI service timed out");
    }

    public static AiException invalidResponse() {
        return new AiException(HttpStatus.BAD_GATEWAY, "Invalid AI service response");
    }

    public static AiException invalidImage() {
        return new AiException(HttpStatus.BAD_REQUEST, "Image is required");
    }
}
