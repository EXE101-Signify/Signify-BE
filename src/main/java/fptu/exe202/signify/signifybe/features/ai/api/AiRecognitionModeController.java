package fptu.exe202.signify.signifybe.features.ai.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.signifybe.features.ai.application.AiRecognitionModeService;
import fptu.exe202.signify.signifybe.features.ai.application.AiRecognitionModeService.Mode;
import fptu.exe202.signify.signifybe.features.ai.application.AiRecognitionModeService.ModeUpdate;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/calls/{callId}/recognition-mode")
public class AiRecognitionModeController {
    private final AiRecognitionModeService modes;

    public AiRecognitionModeController(AiRecognitionModeService modes) {
        this.modes = modes;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ModeUpdate>> current(@AuthenticationPrincipal CurrentUser actor,
                                                            @PathVariable @Positive long callId) {
        return ok(modes.current(callId, actor));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ModeUpdate>> change(@AuthenticationPrincipal CurrentUser actor,
                                                           @PathVariable @Positive long callId,
                                                           @Valid @RequestBody ChangeRequest request) {
        return ok(modes.change(callId, actor, request.mode()));
    }

    private ResponseEntity<ApiResponse<ModeUpdate>> ok(ModeUpdate update) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(update));
    }

    public record ChangeRequest(@NotNull Mode mode) { }
}
