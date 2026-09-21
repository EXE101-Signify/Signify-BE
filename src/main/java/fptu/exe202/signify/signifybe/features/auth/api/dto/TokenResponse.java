package fptu.exe202.signify.signifybe.features.auth.api.dto;

/** Expiry timestamps are Unix epoch milliseconds. */
public record TokenResponse(String accessToken, String refreshToken, String tokenType,
                            long accessExpiresAt, long refreshExpiresAt) {
    @Override public String toString() { return "TokenResponse[REDACTED]"; }
}
