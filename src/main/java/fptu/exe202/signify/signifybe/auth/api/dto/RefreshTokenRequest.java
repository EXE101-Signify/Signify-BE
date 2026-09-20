package fptu.exe202.signify.signifybe.auth.api.dto;

import jakarta.validation.constraints.*;

public record RefreshTokenRequest(@NotBlank @Size(max = 4096) String refreshToken) {
    @Override public String toString() { return "RefreshTokenRequest[REDACTED]"; }
}
