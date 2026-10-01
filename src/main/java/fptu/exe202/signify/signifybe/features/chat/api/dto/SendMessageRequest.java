package fptu.exe202.signify.signifybe.features.chat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SendMessageRequest(
        @NotBlank(message = "Message content must not be blank")
        @Size(max = 5000, message = "Message content must not exceed 5000 characters")
        String content,

        @NotNull(message = "Message type is required")
        String messageType
) {
}
