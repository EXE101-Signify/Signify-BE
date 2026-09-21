package fptu.exe202.signify.signifybe.features.auth.api.dto;

import fptu.exe202.signify.signifybe.features.auth.application.TokenPair;

/** Expiry timestamps are Unix epoch milliseconds. */
public record TokenResponse(String accessToken, String refreshToken, String tokenType,
                            long accessExpiresAt, long refreshExpiresAt) {
    public static TokenResponse from(TokenPair pair) {
        return new TokenResponse(pair.accessToken(), pair.refreshToken(), "Bearer",
                pair.accessExpiresAt(), pair.refreshExpiresAt());
    }
    @Override public String toString() { return "TokenResponse[REDACTED]"; }
}
