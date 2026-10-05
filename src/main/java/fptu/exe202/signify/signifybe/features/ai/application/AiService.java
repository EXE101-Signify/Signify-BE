package fptu.exe202.signify.signifybe.features.ai.application;

import fptu.exe202.signify.signifybe.features.ai.api.dto.AiPredictionResponse;
import fptu.exe202.signify.signifybe.features.ai.domain.exception.AiException;
import fptu.exe202.signify.signifybe.features.ai.infrastructure.AiClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
public class AiService {
    private final AiClient client;

    public AiService(AiClient client) {
        this.client = client;
    }

    /** Calls the external AI service with one encoded image; no prediction is stored or broadcast. */
    public AiPredictionResponse predict(byte[] image, MediaType imageType) {
        if (image == null || image.length == 0 || imageType == null
                || !"image".equalsIgnoreCase(imageType.getType())) {
            throw AiException.invalidImage();
        }
        return client.predict(image, imageType);
    }
}
