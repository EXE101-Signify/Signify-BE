package fptu.exe202.signify.signifybe.features.chat.api.dto;

public record MessageResponse(
        long messageId,
        long conversationId,
        long senderId,
        String content,
        String messageType,
        long createdAt
) {
}
