package fptu.exe202.signify.signifybe.features.ai.application;

import java.util.UUID;

/** Authoritative live spelling text for one call. */
public record AiTextUpdateEvent(String eventId, String type, long callId, long conversationId,
                                String text, long timestamp) {
    public static final String TYPE = "AI_TEXT_UPDATE";

    public static AiTextUpdateEvent of(long callId, long conversationId, String text, long timestamp) {
        return new AiTextUpdateEvent(UUID.randomUUID().toString(), TYPE, callId, conversationId, text, timestamp);
    }
}
