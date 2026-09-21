package fptu.exe202.signify.signifybe.features.storage.domain;

import org.springframework.http.HttpStatus;

public final class StorageDeleteException extends StorageException {
    public StorageDeleteException() {
        super(HttpStatus.BAD_GATEWAY, "Image deletion failed");
    }
}
