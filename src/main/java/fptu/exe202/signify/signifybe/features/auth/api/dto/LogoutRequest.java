package fptu.exe202.signify.signifybe.features.auth.api.dto;

import jakarta.validation.constraints.*;

public record LogoutRequest(@NotBlank @Size(max = 4096) String refreshToken) {
    @Override public String toString() { return "LogoutRequest[REDACTED]"; }
}
