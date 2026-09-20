package fptu.exe202.signify.signifybe.storage.domain;

import fptu.exe202.signify.apiresponse.exception.BaseException;
import org.springframework.http.HttpStatus;

public class StorageException extends BaseException {
    protected StorageException(HttpStatus status, String message) {
        super(status, message);
    }

    public static StorageException invalidRequest(String message) {
        return new StorageException(HttpStatus.BAD_REQUEST, message);
    }

    public static StorageException unavailable() {
        return new StorageException(HttpStatus.BAD_GATEWAY, "Object storage is unavailable");
    }
}
