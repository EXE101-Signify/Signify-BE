package fptu.exe202.signify.signifybe.features.auth.api.dto;

import fptu.exe202.signify.signifybe.features.auth.application.AuthResult;
import fptu.exe202.signify.signifybe.features.user.api.dto.UserResponse;
import fptu.exe202.signify.signifybe.features.user.mapper.UserMapper;

public record AuthResponse(UserResponse user, TokenResponse tokens) {
    public static AuthResponse from(AuthResult result) {
        return new AuthResponse(UserMapper.toResponse(result.user()), TokenResponse.from(result.tokens()));
    }
}
