package fptu.exe202.signify.signifybe.auth.api.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(@NotBlank @Size(max = 100) String username,
                              @NotBlank @Size(min = 8, max = 72) String password,
                              @Email @Size(max = 150) String email,
                              @Size(max = 100) String firstName,
                              @Size(max = 100) String lastName) {
    @Override public String toString() { return "RegisterRequest[REDACTED]"; }
}
