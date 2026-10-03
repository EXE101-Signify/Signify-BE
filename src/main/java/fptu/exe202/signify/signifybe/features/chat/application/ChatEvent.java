package fptu.exe202.signify.signifybe.features.chat.application;

/** Public realtime fields only. eventId lets clients deduplicate an event after reconnect. */
public record ChatEvent(String eventId, String type, long conversationId, Long messageId,
                        long senderId, String content, String messageType, Long createdAt,
                        Long editedAt, Long attachmentId) {
    public static ChatEvent created(long conversationId, long messageId, long senderId,
                                    String content, String messageType, long createdAt, Long attachmentId) {
        return new ChatEvent("MESSAGE_CREATED:" + messageId, "MESSAGE_CREATED", conversationId,
                messageId, senderId, content, messageType, createdAt, null, attachmentId);
    }

    public static ChatEvent updated(long conversationId, long messageId, long senderId,
                                    String content, String messageType, long createdAt, long editedAt) {
        return new ChatEvent("MESSAGE_UPDATED:" + messageId + ":" + editedAt, "MESSAGE_UPDATED",
                conversationId, messageId, senderId, content, messageType, createdAt, editedAt, null);
    }

    public static ChatEvent deleted(long conversationId, long messageId, long senderId) {
        return new ChatEvent("MESSAGE_DELETED:" + messageId, "MESSAGE_DELETED", conversationId,
                messageId, senderId, null, null, null, null, null);
    }
}
