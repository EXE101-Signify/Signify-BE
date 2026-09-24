package fptu.exe202.signify.signifybe.features.storage.application;

import fptu.exe202.signify.signifybe.common.StorageValidation;
import fptu.exe202.signify.signifybe.features.storage.application.port.out.ObjectStorage;
import fptu.exe202.signify.signifybe.features.storage.domain.*;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;

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
        StorageValidation.validateFilename(file.getOriginalFilename());
        String key = StorageKey.generate(file.getContentType());
        try (var stream = new BufferedInputStream(file.getInputStream())) {
            stream.mark(StorageValidation.IMAGE_HEADER_LENGTH);
            byte[] header = stream.readNBytes(StorageValidation.IMAGE_HEADER_LENGTH);
            stream.reset();
            if (!StorageValidation.matchesSignature(header, file.getContentType())) {
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

}
