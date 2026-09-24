package fptu.exe202.signify.signifybe.common;

import fptu.exe202.signify.apiresponse.exception.ConflictException;
import java.nio.charset.StandardCharsets;

public final class UserValidation {

    public static final String EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    public static final String PHONE_NUMBER_REGEX = "^(0|\\+84)(3|5|7|8|9)[0-9]{8}$";
    public static final int USERNAME_MAX_LENGTH = 100;
    public static final int NAME_MAX_LENGTH = 100;
    public static final int FULL_NAME_MAX_LENGTH = 200;
    public static final int EMAIL_MAX_LENGTH = 150;
    public static final int PHONE_MAX_LENGTH = 20;
    public static final int AVATAR_MAX_LENGTH = 2048;
    public static final int ADDRESS_MAX_LENGTH = 500;
    public static final int SEARCH_MAX_LENGTH = 150;
    public static final int PAGE_MAX_SIZE = 100;
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_BYTES = 72;
    public static final String NON_BLANK_REGEX = ".*\\S.*";
    public static final String PASSWORD_LOWERCASE_REGEX = ".*[a-z].*";
    public static final String PASSWORD_UPPERCASE_REGEX = ".*[A-Z].*";
    public static final String PASSWORD_DIGIT_REGEX = ".*\\d.*";
    public static final String PASSWORD_SPECIAL_REGEX = ".*[^A-Za-z\\d].*";

    private UserValidation() { }

    public static String formatFullName(String firstName, String lastName) {
        String name = ((firstName == null ? "" : firstName) + " "
                + (lastName == null ? "" : lastName)).strip();
        return name.isEmpty() ? null : name.substring(0, Math.min(name.length(), FULL_NAME_MAX_LENGTH));
    }

    /** BCrypt accepts at most 72 UTF-8 bytes, not necessarily 72 characters. */
    public static boolean isValidLoginPassword(String password) {
        return password != null && !password.isBlank()
                && password.getBytes(StandardCharsets.UTF_8).length <= PASSWORD_MAX_BYTES;
    }

    public static void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new ConflictException("Password must not be empty.");
        }

        if (!isValidLoginPassword(password)) {
            throw new ConflictException("Password must contain at most 72 UTF-8 bytes.");
        }

        if (password.length() < PASSWORD_MIN_LENGTH) {
            throw new ConflictException("Password must be at least 8 characters long.");
        }

        if (!password.matches(PASSWORD_LOWERCASE_REGEX)) {
            throw new ConflictException("Password must contain at least one lowercase letter.");
        }

        if (!password.matches(PASSWORD_UPPERCASE_REGEX)) {
            throw new ConflictException("Password must contain at least one uppercase letter.");
        }

        if (!password.matches(PASSWORD_DIGIT_REGEX)) {
            throw new ConflictException("Password must contain at least one number.");
        }

        if (!password.matches(PASSWORD_SPECIAL_REGEX)) {
            throw new ConflictException("Password must contain at least one special character.");
        }
    }
}
