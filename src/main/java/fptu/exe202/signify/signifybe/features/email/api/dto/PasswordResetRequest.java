package fptu.exe202.signify.signifybe.features.email.api.dto;

import fptu.exe202.signify.signifybe.common.UserValidation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 6) String otp,
        @NotBlank @Size(min = UserValidation.PASSWORD_MIN_LENGTH, max = UserValidation.PASSWORD_MAX_BYTES) String newPassword
) {}
