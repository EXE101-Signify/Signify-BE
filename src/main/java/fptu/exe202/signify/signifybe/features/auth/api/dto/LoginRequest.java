package fptu.exe202.signify.signifybe.features.auth.api.dto;

import fptu.exe202.signify.signifybe.common.UserValidation;
import fptu.exe202.signify.signifybe.common.AuthValidation;
import jakarta.validation.constraints.*;

public record LoginRequest(@NotBlank @Size(max = UserValidation.USERNAME_MAX_LENGTH) String username,
                           @NotBlank @Size(max = UserValidation.PASSWORD_MAX_BYTES) String password,
                           @Size(max = AuthValidation.DEVICE_NAME_MAX_LENGTH) String deviceName) {
    @Override public String toString() { return "LoginRequest[REDACTED]"; }
}
