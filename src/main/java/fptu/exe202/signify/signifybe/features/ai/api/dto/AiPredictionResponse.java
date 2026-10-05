package fptu.exe202.signify.signifybe.features.ai.api.dto;

public record AiPredictionResponse(String letter, Double confidence, Long timestamp) {
    public boolean isValid() {
        return letter != null && letter.matches("[A-Z]")
                && confidence != null && Double.isFinite(confidence)
                && confidence >= 0 && confidence <= 1
                && timestamp != null && timestamp > 0;
    }
}
