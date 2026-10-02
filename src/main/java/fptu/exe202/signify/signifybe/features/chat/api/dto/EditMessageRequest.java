package fptu.exe202.signify.signifybe.features.chat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditMessageRequest(
        @NotBlank(message = "Message content must not be blank")
        @Size(max = 5000, message = "Message content must not exceed 5000 characters")
        String content
) { }
