package fptu.exe202.signify.signifybe.features.notification.domain.exception;

import fptu.exe202.signify.apiresponse.exception.BaseException;
import org.springframework.http.HttpStatus;

public final class NotificationException extends BaseException {
    private NotificationException(HttpStatus status, String message) {
        super(status, message);
    }

    public static NotificationException notFound() {
        return new NotificationException(HttpStatus.NOT_FOUND, "Notification not found");
    }
}
