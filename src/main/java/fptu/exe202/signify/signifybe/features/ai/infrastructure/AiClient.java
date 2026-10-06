package fptu.exe202.signify.signifybe.features.ai.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fptu.exe202.signify.signifybe.features.ai.api.dto.AiPredictionResponse;
import fptu.exe202.signify.signifybe.features.ai.domain.exception.AiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.core.io.ByteArrayResource;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeUnit;

@Component
public class AiClient {
    private static final Logger log = LoggerFactory.getLogger(AiClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final RestClient http;

    public AiClient(RestClient aiRestClient) {
        this.http = aiRestClient;
    }

    public AiPredictionResponse predict(byte[] image, MediaType imageType) {
        var resource = new ByteArrayResource(image) {
            @Override public String getFilename() { return "image"; }
        };
        var imageHeaders = new HttpHeaders();
        imageHeaders.setContentType(imageType);
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("image", new HttpEntity<>(resource, imageHeaders));

        long started = System.nanoTime();
        log.debug("AI prediction request started");
        try {
            AiPredictionResponse response = http.post()
                    .uri("/api/v1/predict")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(AiPredictionResponse.class);
            if (response == null || !response.isValid()) {
                log.warn("AI prediction returned invalid response");
                return null;
            }
            log.debug("AI prediction completed in {} ms: letter={}, confidence={}",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                    response.letter(), response.confidence());
            return response;
        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                log.warn("AI prediction timed out after {} ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                throw AiException.timeout();
            }
            log.warn("AI service unavailable");
            throw AiException.unavailable();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 422) {
                String reason = rejectionReason(ex.getResponseBodyAsString());
                if ("No hand detected".equals(reason)) throw new AiFrameRejectedException(true);
                if ("Low confidence".equals(reason)) throw new AiFrameRejectedException(false);
            }
            log.warn("AI service returned HTTP {}", ex.getStatusCode().value());
            throw AiException.unavailable();
        } catch (RestClientException ex) {
            log.warn("AI prediction response could not be decoded");
            return null;
        }
    }

    private static String rejectionReason(String body) {
        try {
            var parsed = JSON.readTree(body);
            return parsed == null ? "" : parsed.path("error").asText();
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            return "";
        }
    }

    private static boolean isTimeout(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) return true;
        }
        return false;
    }
}
