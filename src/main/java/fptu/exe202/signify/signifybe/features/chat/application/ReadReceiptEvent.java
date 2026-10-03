package fptu.exe202.signify.signifybe.features.chat.application;

public record ReadReceiptEvent(String eventId, String type, long conversationId, long userId,
                               long lastReadMessageId, long timestamp) {
    public static ReadReceiptEvent of(long conversationId, long userId, long messageId, long timestamp) {
        return new ReadReceiptEvent("READ_RECEIPT:" + conversationId + ":" + userId + ":" + messageId,
                "READ_RECEIPT", conversationId, userId, messageId, timestamp);
    }
}
