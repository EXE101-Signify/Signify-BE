package fptu.exe202.signify.signifybe.features.email.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SendOtpRequest(
        @NotBlank @Email String email
) {}
