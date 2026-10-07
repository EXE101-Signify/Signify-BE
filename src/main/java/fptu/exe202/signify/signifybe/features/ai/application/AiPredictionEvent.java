package fptu.exe202.signify.signifybe.features.ai.application;

import fptu.exe202.signify.signifybe.features.ai.api.dto.AiPredictionResponse;

import java.util.UUID;

/** Public realtime prediction fields; eventId supports client deduplication. */
public record AiPredictionEvent(String eventId, String type, long conversationId, long callId,
                                String letter, double confidence, long timestamp) {
    public static final String TYPE = "AI_SIGN_PREDICTION";

    public static AiPredictionEvent from(long conversationId, long callId, AiPredictionResponse prediction) {
        return new AiPredictionEvent(UUID.randomUUID().toString(), TYPE, conversationId, callId,
                prediction.letter(), prediction.confidence(), prediction.timestamp());
    }
}
