package fptu.exe202.signify.signifybe.features.storage.domain;

import org.springframework.http.HttpStatus;

public final class StorageObjectNotFoundException extends StorageException {
    public StorageObjectNotFoundException() {
        super(HttpStatus.NOT_FOUND, "Image not found");
    }
}
