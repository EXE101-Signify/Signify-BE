package fptu.exe202.signify.signifybe.features.auth.api.dto;

import fptu.exe202.signify.signifybe.common.AuthValidation;
import jakarta.validation.constraints.*;

public record RefreshTokenRequest(@NotBlank @Size(max = AuthValidation.TOKEN_MAX_LENGTH) String refreshToken) {
    @Override public String toString() { return "RefreshTokenRequest[REDACTED]"; }
}
