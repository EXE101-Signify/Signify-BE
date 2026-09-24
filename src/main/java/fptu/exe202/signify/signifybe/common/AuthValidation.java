package fptu.exe202.signify.signifybe.common;

public final class AuthValidation {
    public static final int TOKEN_MAX_LENGTH = 4096;
    public static final int DEVICE_NAME_MAX_LENGTH = 255;
    public static final int IP_ADDRESS_MAX_LENGTH = 100;
    public static final int USER_AGENT_MAX_LENGTH = 1024;
    public static final String REFRESH_TOKEN_ID_REGEX = "[A-Za-z0-9_-]{43}";

    private AuthValidation() { }

    public static void validateTokenLength(String token) {
        if (token == null || token.isBlank() || token.length() > TOKEN_MAX_LENGTH) {
            throw new IllegalArgumentException();
        }
    }

    public static String formatMetadata(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String clean = value.replaceAll("[\\p{Cntrl}]", "").strip();
        return clean.substring(0, Math.min(clean.length(), max));
    }
}
