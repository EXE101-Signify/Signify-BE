package fptu.exe202.signify.signifybe.features.call.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.call.api.dto.CreateVideoCallRequest;
import fptu.exe202.signify.signifybe.features.call.api.dto.VideoCallResponse;
import fptu.exe202.signify.signifybe.features.call.application.VideoCallService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/calls")
public class VideoCallController {
    private final VideoCallService calls;

    public VideoCallController(VideoCallService calls) { this.calls = calls; }

    @PostMapping
    public ResponseEntity<ApiResponse<VideoCallResponse>> create(@AuthenticationPrincipal CurrentUser user,
                                                                  @Valid @RequestBody CreateVideoCallRequest request) {
        return ok(VideoCallResponse.from(calls.create(request.conversationId(), user)));
    }

    @PostMapping("/{callId}/accept")
    public ResponseEntity<ApiResponse<VideoCallResponse>> accept(@AuthenticationPrincipal CurrentUser user,
                                                                  @PathVariable @Positive long callId) {
        return ok(VideoCallResponse.from(calls.accept(callId, user)));
    }

    @PostMapping("/{callId}/reject")
    public ResponseEntity<ApiResponse<VideoCallResponse>> reject(@AuthenticationPrincipal CurrentUser user,
                                                                  @PathVariable @Positive long callId) {
        return ok(VideoCallResponse.from(calls.reject(callId, user)));
    }

    @PostMapping("/{callId}/end")
    public ResponseEntity<ApiResponse<VideoCallResponse>> end(@AuthenticationPrincipal CurrentUser user,
                                                               @PathVariable @Positive long callId) {
        return ok(VideoCallResponse.from(calls.complete(callId, user)));
    }

    @PostMapping("/{callId}/busy")
    public ResponseEntity<ApiResponse<VideoCallResponse>> busy(@AuthenticationPrincipal CurrentUser user,
                                                                @PathVariable @Positive long callId) {
        return ok(VideoCallResponse.from(calls.busy(callId, user)));
    }

    private ResponseEntity<ApiResponse<VideoCallResponse>> ok(VideoCallResponse data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
    }
}
