package fptu.exe202.signify.signifybe.features.auth.domain.exception;

import fptu.exe202.signify.apiresponse.exception.BaseException;
import org.springframework.http.HttpStatus;

/** Only safe messages cross the shared API exception handler. */
public final class AuthException extends BaseException {
    private AuthException(HttpStatus status, String message) { super(status, message); }

    public static AuthException invalidCredentials() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }
    public static AuthException accountDisabled() {
        return new AuthException(HttpStatus.FORBIDDEN, "Account is not active");
    }
    public static AuthException invalidRefreshToken() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired");
    }
    public static AuthException invalidAccessToken() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "Access token is invalid or expired");
    }
}
