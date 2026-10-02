package fptu.exe202.signify.signifybe.features.chat.application;

public record AttachmentMessageResponse(long messageId, long attachmentId, long conversationId,
        long senderId, String content, String fileName, String mimeType, long fileSize, long createdAt) { }
