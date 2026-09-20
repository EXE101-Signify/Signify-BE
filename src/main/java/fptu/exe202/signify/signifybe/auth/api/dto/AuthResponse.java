package fptu.exe202.signify.signifybe.auth.api.dto;

import fptu.exe202.signify.signifybe.auth.application.AuthResult;

public record AuthResponse(UserResponse user, TokenResponse tokens) {
    public static AuthResponse from(AuthResult result) {
        return new AuthResponse(UserResponse.from(result.user()), TokenResponse.from(result.tokens()));
    }
}
