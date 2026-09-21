package fptu.exe202.signify.signifybe.features.auth.api.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(@NotBlank @Size(max = 100) String username,
                           @NotBlank @Size(max = 72) String password,
                           @Size(max = 255) String deviceName) {
    @Override public String toString() { return "LoginRequest[REDACTED]"; }
}
