package fptu.exe202.signify.signifybe.features.storage.domain;

import org.springframework.http.HttpStatus;

public final class StorageUploadException extends StorageException {
    public StorageUploadException() {
        super(HttpStatus.BAD_GATEWAY, "Image upload failed");
    }
}
