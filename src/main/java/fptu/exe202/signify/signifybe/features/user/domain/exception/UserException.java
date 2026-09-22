package fptu.exe202.signify.signifybe.features.user.domain.exception;
import fptu.exe202.signify.apiresponse.exception.BaseException;
import org.springframework.http.HttpStatus;

public final class UserException extends BaseException {
    public static UserException notFound() {
        return new UserException(HttpStatus.NOT_FOUND, "User not found");
    }
    public static UserException cannotBanSelf() {
        return new UserException(HttpStatus.FORBIDDEN, "Administrators cannot ban themselves");
    }
    private UserException(HttpStatus status, String message) { super(status, message); }
    public static UserException usernameTaken() {
        return new UserException(HttpStatus.CONFLICT, "Username already exists");
    }
    public static UserException registrationConflict() {
        return new UserException(HttpStatus.CONFLICT, "Account could not be registered");
    }
    public static UserException invalidPassword() {
        return new UserException(HttpStatus.BAD_REQUEST, "Password must contain at least 8 characters and at most 72 UTF-8 bytes");
    }
}
