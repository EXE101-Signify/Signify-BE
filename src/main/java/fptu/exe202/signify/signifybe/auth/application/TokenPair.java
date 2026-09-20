package fptu.exe202.signify.signifybe.auth.application;

public record TokenPair(String accessToken, String refreshToken, long accessExpiresAt, long refreshExpiresAt) {
    @Override public String toString() { return "TokenPair[REDACTED]"; }
}
