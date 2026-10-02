package fptu.exe202.signify.signifybe.features.chat.application;

public record AttachmentResponse(long attachmentId, long messageId, String fileName,
        String mimeType, long fileSize, long createdAt, String url) { }
