package fptu.exe202.signify.signifybe.features.storage.application;

import fptu.exe202.signify.signifybe.common.StorageValidation;
import fptu.exe202.signify.signifybe.features.storage.application.port.out.ObjectStorage;
import fptu.exe202.signify.signifybe.features.storage.domain.*;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StorageService {

    ObjectStorage objectStorage;
    ImageProperties imageProperties;

    public StorageService(
            ObjectStorage objectStorage,
            ImageProperties imageProperties
    ) {
        this.objectStorage = objectStorage;
        this.imageProperties = imageProperties;
    }

    /**
     * Upload avatar:
     * avatars/{userId}/{generated-file-name}
     */
    public StorageObject uploadAvatar(Long userId, MultipartFile file) {
        validateId(userId, "userId");

        return uploadImage(
                "avatars/" + userId + "/",
                file
        );
    }

    /**
     * Upload image for a chat attachment:
     * chat-attachments/{conversationId}/{generated-file-name}
     *
     * This method currently accepts images only.
     */
    public StorageObject uploadChatImage(
            Long conversationId,
            MultipartFile file
    ) {
        validateId(conversationId, "conversationId");

        return uploadImage(
                "chat-attachments/" + conversationId + "/",
                file
        );
    }

    /**
     * Shared image validation and upload logic.
     */
    private StorageObject uploadImage(
            String prefix,
            MultipartFile file
    ) {
        validateImage(file);

        String key = prefix
                + StorageKey.generate(file.getContentType());

        try {
            byte[] bytes = file.getBytes();

            byte[] header;
            try (var input = new ByteArrayInputStream(bytes)) {
                header = input.readNBytes(
                        StorageValidation.IMAGE_HEADER_LENGTH
                );
            }

            if (!StorageValidation.matchesSignature(
                    header,
                    file.getContentType()
            )) {
                throw new UnsupportedImageTypeException();
            }

            try (var uploadStream = new ByteArrayInputStream(bytes)) {
                return objectStorage.upload(
                        key,
                        uploadStream,
                        file.getContentType(),
                        bytes.length
                );
            }

        } catch (IOException ex) {
            throw new StorageUploadException();
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw StorageException.invalidRequest(
                    "Image must not be empty"
            );
        }

        if (file.getSize() > imageProperties.maxSize()) {
            throw new ImageSizeExceededException();
        }

        StorageValidation.validateFilename(
                file.getOriginalFilename()
        );
    }

    private void validateId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw StorageException.invalidRequest(
                    "Invalid " + fieldName
            );
        }
    }

    public void deleteImage(String key) {
        StorageKey.validate(key);
        objectStorage.delete(key);
    }

    public StorageObject uploadChatAttachment(long conversationId, MultipartFile file) {
        if (conversationId <= 0 || file == null || file.isEmpty() || file.getSize() <= 0) {
            throw StorageException.invalidRequest("Attachment must not be empty");
        }
        if (file.getSize() > imageProperties.maxSize()) {
            throw new AttachmentSizeExceededException();
        }
        String mimeType = file.getContentType();
        String extension = StorageValidation.attachmentExtension(mimeType, file.getOriginalFilename());
        String key = "chat-attachments/" + conversationId + "/" + UUID.randomUUID() + "." + extension;
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0 || bytes.length > imageProperties.maxSize()
                    || !StorageValidation.matchesAttachmentSignature(bytes, mimeType)) {
                throw StorageException.invalidRequest("Invalid attachment content or size");
            }
            try (var input = new ByteArrayInputStream(bytes)) {
                try {
                    return objectStorage.upload(key, input, mimeType, bytes.length);
                } catch (RuntimeException uploadFailure) {
                    // A provider may have written the object before its response failed.
                    try { objectStorage.delete(key); }
                    catch (RuntimeException cleanupFailure) { uploadFailure.addSuppressed(cleanupFailure); }
                    throw uploadFailure;
                }
            }
        } catch (IOException ex) {
            throw new StorageUploadException();
        }
    }

    public void deleteAttachment(String key) {
        StorageValidation.validateAttachmentKey(key);
        objectStorage.delete(key);
    }

    public String generateAttachmentUrl(String key) {
        StorageValidation.validateAttachmentKey(key);
        if (!objectStorage.exists(key)) throw new StorageObjectNotFoundException();
        return objectStorage.generatePrivateUrl(key);
    }

    public String generateUrl(String key) {
        StorageKey.validate(key);

        if (!objectStorage.exists(key)) {
            throw new StorageObjectNotFoundException();
        }

        return objectStorage.generateUrl(key);
    }
}
