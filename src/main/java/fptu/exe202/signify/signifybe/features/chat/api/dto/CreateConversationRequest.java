package fptu.exe202.signify.signifybe.features.chat.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateConversationRequest(
        @NotNull(message = "Conversation type is required")
        String type,

        @Size(max = 100, message = "Conversation name must not exceed 100 characters")
        String name,

        @NotEmpty(message = "Participant IDs are required")
        List<Long> participantIds
) {
}
