package fptu.exe202.signify.signifybe.features.chat.application;

import java.util.List;

public record MessageHistoryResponse(List<Item> messages, Long nextCursor, boolean hasMore) {
    public record Item(long messageId, long conversationId, long senderId, String content,
                       String messageType, long createdAt, Long editedAt, List<Attachment> attachments) { }
    public record Attachment(long attachmentId, String fileName, String mimeType,
                             long fileSize, long createdAt) { }
}
