package fptu.exe202.signify.signifybe.features.chat.api.dto;

public record LastMessageResponse(
        long messageId,
        long senderId,
        String content,
        String messageType,
        long createdAt
) {
}
