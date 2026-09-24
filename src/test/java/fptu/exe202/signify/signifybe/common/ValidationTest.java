package fptu.exe202.signify.signifybe.common;

import fptu.exe202.signify.apiresponse.exception.ConflictException;
import fptu.exe202.signify.signifybe.features.storage.domain.StorageException;
import fptu.exe202.signify.signifybe.features.storage.domain.StorageKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidationTest {
    @Test
    void registrationAndLoginRespectUtf8ByteLimit() {
        String atLimit = "Aa1!" + "é".repeat(34);
        String overLimit = atLimit + "é";
        assertDoesNotThrow(() -> UserValidation.validatePassword(atLimit));
        assertTrue(UserValidation.isValidLoginPassword(atLimit));
        assertThrows(ConflictException.class, () -> UserValidation.validatePassword(overLimit));
        assertFalse(UserValidation.isValidLoginPassword(overLimit));
    }

    @Test
    void fullNameHandlesAbsentNamesAndDatabaseLimit() {
        assertNull(UserValidation.formatFullName(null, "  "));
        assertEquals("Nguyen Van", UserValidation.formatFullName(" Nguyen", "Van "));
        assertEquals(UserValidation.FULL_NAME_MAX_LENGTH,
                UserValidation.formatFullName("a".repeat(100), "b".repeat(100)).length());
    }

    @Test
    void tokenLengthRejectsBlankAndOversizedValues() {
        assertThrows(IllegalArgumentException.class, () -> AuthValidation.validateTokenLength(null));
        assertThrows(IllegalArgumentException.class, () -> AuthValidation.validateTokenLength(" "));
        assertDoesNotThrow(() -> AuthValidation.validateTokenLength("a".repeat(4096)));
        assertThrows(IllegalArgumentException.class,
                () -> AuthValidation.validateTokenLength("a".repeat(4097)));
        assertEquals("device", AuthValidation.formatMetadata(" \u0000device\n ", 255));
    }

    @Test
    void storageRejectsUnsafeNamesAndMismatchedImageTypes() {
        assertThrows(StorageException.class, () -> StorageValidation.validateFilename("../image.png"));
        assertThrows(StorageException.class, () -> StorageValidation.validateFilename("C:\\image.png"));
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        assertTrue(StorageValidation.matchesSignature(png, "image/png"));
        assertFalse(StorageValidation.matchesSignature(png, "image/jpeg"));
        assertFalse(StorageValidation.matchesSignature(new byte[0], "image/png"));
        assertFalse(StorageValidation.matchesSignature(png, null));
        assertDoesNotThrow(() -> StorageKey.validate(StorageKey.generate("image/png")));
        assertThrows(StorageException.class, () -> StorageKey.validate("images/../image.png"));
    }
}
