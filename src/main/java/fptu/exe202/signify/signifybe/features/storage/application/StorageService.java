package fptu.exe202.signify.signifybe.features.storage.application;

import fptu.exe202.signify.signifybe.features.storage.application.port.out.ObjectStorage;
import fptu.exe202.signify.signifybe.features.storage.domain.*;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StorageService {
    ObjectStorage objectStorage;
    ImageProperties imageProperties;

    public StorageService(ObjectStorage objectStorage, ImageProperties imageProperties) {
        this.objectStorage = objectStorage;
        this.imageProperties = imageProperties;
    }

    public StorageObject uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw StorageException.invalidRequest("Image must not be empty");
        }
        if (file.getSize() > imageProperties.maxSize()) {
            throw new ImageSizeExceededException();
        }
        validateFilename(file.getOriginalFilename());
        String key = StorageKey.generate(file.getContentType());
        try (var stream = new BufferedInputStream(file.getInputStream())) {
            stream.mark(12);
            byte[] header = stream.readNBytes(12);
            stream.reset();
            if (!matchesSignature(header, file.getContentType())) {
                throw new UnsupportedImageTypeException();
            }
            return objectStorage.upload(key, stream, file.getContentType(), file.getSize());
        } catch (IOException ex) {
            throw new StorageUploadException();
        }
    }

    public void deleteImage(String key) {
        StorageKey.validate(key);
        objectStorage.delete(key);
    }

    public String generateUrl(String key) {
        StorageKey.validate(key);
        if (!objectStorage.exists(key)) {
            throw new StorageObjectNotFoundException();
        }
        return objectStorage.generateUrl(key);
    }

    private void validateFilename(String filename) {
        if (filename == null || filename.isBlank() || filename.length() > 255
                || filename.equals(".") || filename.equals("..")
                || filename.indexOf('/') >= 0 || filename.indexOf('\\') >= 0
                || filename.indexOf(':') >= 0 || filename.chars().anyMatch(Character::isISOControl)) {
            throw StorageException.invalidRequest("Invalid image filename");
        }
    }

    // Validate magic bytes as well as the untrusted multipart Content-Type.
    // This is format identification, not a full image decoder or malware scanner.
    private boolean matchesSignature(byte[] header, String contentType) {
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

    private String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }
}
