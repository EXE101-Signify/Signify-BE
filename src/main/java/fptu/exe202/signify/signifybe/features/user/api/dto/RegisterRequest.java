package fptu.exe202.signify.signifybe.features.user.api.dto;

import fptu.exe202.signify.signifybe.common.UserValidation;
import jakarta.validation.constraints.*;

public record RegisterRequest(@NotBlank @Size(max = UserValidation.USERNAME_MAX_LENGTH) String username,
                              @NotBlank @Size(min = UserValidation.PASSWORD_MIN_LENGTH, max = UserValidation.PASSWORD_MAX_BYTES) String password,
                              @NotBlank @Email @Size(max = UserValidation.EMAIL_MAX_LENGTH) String email,
                              @NotBlank @Pattern(regexp = "[0-9]{6}") String otp,
                              @Size(max = UserValidation.NAME_MAX_LENGTH) String firstName,
                              @Size(max = UserValidation.NAME_MAX_LENGTH) String lastName) {
}
