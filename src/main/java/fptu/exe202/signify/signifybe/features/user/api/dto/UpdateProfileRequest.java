package fptu.exe202.signify.signifybe.features.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Missing/null fields are unchanged. Identity, role and status are never client writable. */
public record UpdateProfileRequest(
        @Email @Size(max = 150) @Pattern(regexp = ".*\\S.*") String email,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 2048) @Pattern(regexp = "(?:|https?://\\S+)") String avatar) { }
