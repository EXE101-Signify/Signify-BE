package fptu.exe202.signify.signifybe.features.ai.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.signifybe.features.ai.application.AiPredictionEvent;
import fptu.exe202.signify.signifybe.features.ai.application.AiPredictionService;
import fptu.exe202.signify.signifybe.features.ai.domain.exception.AiException;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import jakarta.validation.constraints.Positive;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Validated
@RestController
@RequestMapping("/api/calls")
public class AiPredictionController {
    private final AiPredictionService predictions;

    public AiPredictionController(AiPredictionService predictions) { this.predictions = predictions; }

    @PostMapping(value = "/{callId}/predictions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AiPredictionEvent>> predict(@AuthenticationPrincipal CurrentUser user,
            @PathVariable @Positive long callId, @RequestPart("image") MultipartFile image) throws IOException {
        MediaType imageType;
        try {
            imageType = image.getContentType() == null ? null : MediaType.parseMediaType(image.getContentType());
        } catch (IllegalArgumentException ex) {
            throw AiException.invalidImage();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                ApiResponse.success(predictions.predict(callId, user, image.getBytes(), imageType)));
    }
}
