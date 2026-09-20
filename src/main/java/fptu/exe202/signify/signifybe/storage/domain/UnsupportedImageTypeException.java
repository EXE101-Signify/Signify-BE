package fptu.exe202.signify.signifybe.storage.domain;

import org.springframework.http.HttpStatus;

public final class UnsupportedImageTypeException extends StorageException {
    public UnsupportedImageTypeException() {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only JPEG, PNG, WebP and GIF images are supported");
    }
}
