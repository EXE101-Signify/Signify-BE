package fptu.exe202.signify.signifybe.storage.application.port.out;

import fptu.exe202.signify.signifybe.storage.domain.StorageObject;
import java.io.InputStream;

public interface ObjectStorage {
    StorageObject upload(String key, InputStream inputStream, String contentType, long contentLength);

    /** Deletion is idempotent, including when the key no longer exists. */
    void delete(String key);

    String generateUrl(String key);

    boolean exists(String key);
}
