package fptu.exe202.signify.signifybe.features.storage.domain;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public final class StorageKey {
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "image/gif", "gif");
    private static final Pattern IMAGE_KEY = Pattern.compile(
            "images/[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.(jpg|png|webp|gif)");

    private StorageKey() {
    }

    public static String generate(String contentType) {
        if (contentType == null || !EXTENSIONS.containsKey(contentType)) {
            throw new UnsupportedImageTypeException();
        }
        return "images/" + UUID.randomUUID() + "." + EXTENSIONS.get(contentType);
    }

    public static void validate(String key) {
        if (key == null || !IMAGE_KEY.matcher(key).matches()) {
            throw StorageException.invalidRequest("Invalid image storage key");
        }
    }
}
