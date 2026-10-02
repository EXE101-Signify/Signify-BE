package fptu.exe202.signify.signifybe.features.storage.domain;

import org.springframework.http.HttpStatus;

public final class AttachmentSizeExceededException extends StorageException {
    public AttachmentSizeExceededException() {
        super(HttpStatus.PAYLOAD_TOO_LARGE, "Attachment exceeds the configured maximum size");
    }
}
