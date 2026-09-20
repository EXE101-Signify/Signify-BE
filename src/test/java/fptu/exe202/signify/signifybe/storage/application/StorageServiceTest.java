package fptu.exe202.signify.signifybe.storage.application;

import fptu.exe202.signify.signifybe.storage.application.port.out.ObjectStorage;
import fptu.exe202.signify.signifybe.storage.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.io.IOException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StorageServiceTest {
    private static final String KEY = "images/123e4567-e89b-42d3-a456-426614174000.png";
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a6WQAAAAASUVORK5CYII=");
    private ObjectStorage storage;
    private StorageService service;

    @BeforeEach
    void setUp() {
        storage = mock(ObjectStorage.class);
        service = new StorageService(storage, new ImageProperties(5242880));
    }

    @Test
    void uploadsWithGeneratedKeyAndIntactStream() throws IOException {
        when(storage.upload(anyString(), any(InputStream.class), eq("image/png"), eq((long) PNG.length)))
                .thenAnswer(call -> {
                    String key = call.getArgument(0);
                    StorageKey.validate(key);
                    assertThat(key).doesNotContain("untrusted-name");
                    assertThat(((InputStream) call.getArgument(1)).readAllBytes()).isEqualTo(PNG);
                    return new StorageObject(key, "https://images.example/" + key, "image/png", PNG.length);
                });
        var file = new MockMultipartFile("file", "untrusted-name.png", "image/png", PNG);
        var first = service.uploadImage(file);
        var second = service.uploadImage(file);
        assertThat(first.contentType()).isEqualTo("image/png");
        assertThat(first.size()).isEqualTo(PNG.length);
        assertThat(first.key()).isNotEqualTo(second.key());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"text/plain", "image/svg+xml", "application/octet-stream"})
    void rejectsUnsupportedMimeType(String type) {
        assertThatThrownBy(() -> service.uploadImage(new MockMultipartFile("file", "image.png", type, PNG)))
                .isInstanceOf(UnsupportedImageTypeException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsForgedMimeType() {
        assertThatThrownBy(() -> service.uploadImage(
                new MockMultipartFile("file", "image.png", "image/png", "not an image".getBytes())))
                .isInstanceOf(UnsupportedImageTypeException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsOversizeImage() {
        service = new StorageService(storage, new ImageProperties(PNG.length - 1));
        assertThatThrownBy(() -> service.uploadImage(new MockMultipartFile("file", "image.png", "image/png", PNG)))
                .isInstanceOf(ImageSizeExceededException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void acceptsExactSizeLimit() {
        service = new StorageService(storage, new ImageProperties(PNG.length));
        service.uploadImage(new MockMultipartFile("file", "image.png", "image/png", PNG));
        verify(storage).upload(anyString(), any(InputStream.class), eq("image/png"), eq((long) PNG.length));
    }

    @Test
    void rejectsEmptyOrMissingImage() {
        assertThatThrownBy(() -> service.uploadImage(new MockMultipartFile("file", new byte[0])))
                .isInstanceOf(StorageException.class).hasMessage("Image must not be empty");
        assertThatThrownBy(() -> service.uploadImage(null)).isInstanceOf(StorageException.class);
        verifyNoInteractions(storage);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "../image.png", "C:\\image.png", "folder/image.png", ".", "..", "bad\nname.png"})
    void rejectsUnsafeFilename(String filename) {
        assertThatThrownBy(() -> service.uploadImage(new MockMultipartFile("file", filename, "image/png", PNG)))
                .isInstanceOf(StorageException.class).hasMessage("Invalid image filename");
        verifyNoInteractions(storage);
    }

    @Test
    void translatesReadFailure() throws IOException {
        var file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getSize()).thenReturn(100L);
        when(file.getOriginalFilename()).thenReturn("image.png");
        when(file.getContentType()).thenReturn("image/png");
        when(file.getInputStream()).thenThrow(new IOException("internal details"));
        assertThatThrownBy(() -> service.uploadImage(file)).isInstanceOf(StorageUploadException.class)
                .hasMessage("Image upload failed");
        verifyNoInteractions(storage);
    }

    @Test
    void deletesImage() {
        service.deleteImage(KEY);
        verify(storage).delete(KEY);
    }

    @Test
    void generatesUrlForExistingImage() {
        when(storage.exists(KEY)).thenReturn(true);
        when(storage.generateUrl(KEY)).thenReturn("https://images.example/image");
        assertThat(service.generateUrl(KEY)).isEqualTo("https://images.example/image");
    }

    @Test
    void rejectsMissingImage() {
        assertThatThrownBy(() -> service.generateUrl(KEY)).isInstanceOf(StorageObjectNotFoundException.class);
        verify(storage, never()).generateUrl(anyString());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"../secret", "/etc/passwd", "C:\\file", "images/%2e%2e/private", "other/image.png",
            "images/123e4567-e89b-42d3-a456-426614174000.png?x=1"})
    void rejectsInvalidKeysBeforeCallingProvider(String key) {
        assertThatThrownBy(() -> service.deleteImage(key)).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> service.generateUrl(key)).isInstanceOf(StorageException.class);
        verifyNoInteractions(storage);
    }
}
