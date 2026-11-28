package fptu.exe202.signify.signifybe.features.ai.api.dto;

import fptu.exe202.signify.signifybe.features.ai.application.AiPredictionEvent;

/** Result for one uploaded frame. The accepted event is present only after temporal stabilization. */
public record AiPredictionFrameResponse(
        String letter,
        Double confidence,
        Long timestamp,
        boolean accepted,
        AiPredictionEvent event
) {
    public static AiPredictionFrameResponse of(AiPredictionResponse prediction, AiPredictionEvent event) {
        return new AiPredictionFrameResponse(
                prediction.letter(), prediction.confidence(), prediction.timestamp(), event != null, event);
    }
}
