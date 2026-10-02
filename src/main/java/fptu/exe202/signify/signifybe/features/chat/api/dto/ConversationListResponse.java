package fptu.exe202.signify.signifybe.features.chat.api.dto;

import java.util.List;

public record ConversationListResponse(
        long conversationId,
        String type,
        String name,
        List<ParticipantResponse> participants,
        LastMessageResponse lastMessage,
        long updatedAt
) {
}
