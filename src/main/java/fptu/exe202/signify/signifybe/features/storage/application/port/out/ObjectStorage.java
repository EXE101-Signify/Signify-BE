package fptu.exe202.signify.signifybe.features.storage.application.port.out;

import fptu.exe202.signify.signifybe.features.storage.domain.StorageObject;
import java.io.InputStream;

public interface ObjectStorage {
    StorageObject upload(String key, InputStream inputStream, String contentType, long contentLength);

    /** Deletion is idempotent, including when the key no longer exists. */
    void delete(String key);

    String generateUrl(String key);

    /** Always signs access to a private object, even when public URLs are enabled for avatars. */
    default String generatePrivateUrl(String key) { return generateUrl(key); }

    boolean exists(String key);
}
