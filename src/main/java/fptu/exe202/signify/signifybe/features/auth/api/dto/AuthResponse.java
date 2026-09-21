package fptu.exe202.signify.signifybe.features.auth.api.dto;

import fptu.exe202.signify.signifybe.features.user.api.dto.UserResponse;

public record AuthResponse(UserResponse user, TokenResponse tokens) {
}
