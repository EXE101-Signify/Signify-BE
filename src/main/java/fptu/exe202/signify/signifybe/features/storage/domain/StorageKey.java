package fptu.exe202.signify.signifybe.features.storage.domain;

import fptu.exe202.signify.signifybe.common.StorageValidation;
import java.util.UUID;

public final class StorageKey {
    private StorageKey() { }

    public static String generate(String contentType) {
        String extension = StorageValidation.imageExtension(contentType);
        return UUID.randomUUID() + "." + extension;
    }

    public static void validate(String key) {
        StorageValidation.validateImageKey(key);
    }

    public static void validateObjectKey(String key) {
        try {
            validate(key);
        } catch (fptu.exe202.signify.signifybe.features.storage.domain.StorageException ex) {
            StorageValidation.validateAttachmentKey(key);
        }
    }
}
