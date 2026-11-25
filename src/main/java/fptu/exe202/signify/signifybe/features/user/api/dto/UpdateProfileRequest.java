package fptu.exe202.signify.signifybe.features.user.api.dto;

import fptu.exe202.signify.signifybe.common.UserValidation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Missing/null fields are unchanged. Identity, role and status are never client writable. */
public record UpdateProfileRequest(
        @Email @Size(max = UserValidation.EMAIL_MAX_LENGTH) @Pattern(regexp = UserValidation.NON_BLANK_REGEX) String email,
        @Size(max = UserValidation.NAME_MAX_LENGTH) String firstName,
        @Size(max = UserValidation.NAME_MAX_LENGTH) String lastName,
        @Size(max = UserValidation.AVATAR_MAX_LENGTH) String avatar,
        @Size(max = UserValidation.PHONE_MAX_LENGTH) String phone,
        Boolean gender,
        @Size(max = UserValidation.ADDRESS_MAX_LENGTH) String address) { }
