package fptu.exe202.signify.signifybe.features.chat.api.dto;

import java.util.List;

public record ConversationResponse(
        long conversationId,
        String type,
        String name,
        Long creatorId,
        List<ParticipantResponse> participants,
        long createdAt,
        long updatedAt
) {
}
