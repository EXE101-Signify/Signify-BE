package fptu.exe202.signify.signifybe.features.ai.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.signifybe.features.ai.application.AiTextService;
import fptu.exe202.signify.signifybe.features.ai.application.AiTextUpdateEvent;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import jakarta.validation.constraints.Positive;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/calls/{callId}/text")
public class AiTextController {
    private final AiTextService text;

    public AiTextController(AiTextService text) {
        this.text = text;
    }

    @PostMapping("/space")
    public ResponseEntity<ApiResponse<AiTextUpdateEvent>> space(@AuthenticationPrincipal CurrentUser user,
                                                                @PathVariable @Positive long callId) {
        return ok(text.space(callId, user));
    }

    @PostMapping("/delete")
    public ResponseEntity<ApiResponse<AiTextUpdateEvent>> delete(@AuthenticationPrincipal CurrentUser user,
                                                                 @PathVariable @Positive long callId) {
        return ok(text.delete(callId, user));
    }

    @PostMapping("/clear")
    public ResponseEntity<ApiResponse<AiTextUpdateEvent>> clear(@AuthenticationPrincipal CurrentUser user,
                                                                @PathVariable @Positive long callId) {
        return ok(text.clear(callId, user));
    }

    private ResponseEntity<ApiResponse<AiTextUpdateEvent>> ok(AiTextUpdateEvent event) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(event));
    }
}
