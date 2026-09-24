package fptu.exe202.signify.signifybe.common;

import fptu.exe202.signify.signifybe.features.storage.domain.StorageException;
import fptu.exe202.signify.signifybe.features.storage.domain.UnsupportedImageTypeException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

public final class StorageValidation {
    public static final int FILENAME_MAX_LENGTH = 255;
    public static final int IMAGE_HEADER_LENGTH = 12;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "image/gif", "gif");
    private static final Pattern IMAGE_KEY = Pattern.compile(
            "images/[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.(jpg|png|webp|gif)");


    private StorageValidation() { }

    public static String imageExtension(String contentType) {
        if (contentType == null || !EXTENSIONS.containsKey(contentType)) {
            throw new UnsupportedImageTypeException();
        }
        return EXTENSIONS.get(contentType);
    }

    public static void validateImageKey(String key) {
        if (key == null || !IMAGE_KEY.matcher(key).matches()) {
            throw StorageException.invalidRequest("Invalid image storage key");
        }
    }

    public static void validateFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.length() > FILENAME_MAX_LENGTH
                || filename.equals(".") || filename.equals("..")
                || filename.indexOf('/') >= 0 || filename.indexOf('\\') >= 0
                || filename.indexOf(':') >= 0 || filename.chars().anyMatch(Character::isISOControl)) {
            throw StorageException.invalidRequest("Invalid image filename");
        }
    }

    // Validate magic bytes as well as the untrusted multipart Content-Type.
    // This is format identification, not a full image decoder or malware scanner.
    public static boolean matchesSignature(byte[] header, String contentType) {
        if (header == null || contentType == null) return false;
        return switch (contentType) {
            case "image/jpeg" -> header.length >= 3 && (header[0] & 0xff) == 0xff
                    && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff;
            case "image/png" -> header.length >= 8 && (header[0] & 0xff) == 0x89
                    && ascii(header, 1, 3).equals("PNG") && header[4] == 13 && header[5] == 10
                    && header[6] == 26 && header[7] == 10;
            case "image/gif" -> header.length >= 6
                    && (ascii(header, 0, 6).equals("GIF87a") || ascii(header, 0, 6).equals("GIF89a"));
            case "image/webp" -> header.length >= 12 && ascii(header, 0, 4).equals("RIFF")
                    && ascii(header, 8, 4).equals("WEBP");
            default -> false;
        };
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }
}
