package fptu.exe202.signify.signifybe.features.user.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.signifybe.common.UserValidation;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.user.api.dto.FriendRelationshipResponse;
import fptu.exe202.signify.signifybe.features.user.api.dto.FriendRequestResponse;
import fptu.exe202.signify.signifybe.features.user.api.dto.FriendResponse;
import fptu.exe202.signify.signifybe.features.user.api.dto.FriendSearchResponse;
import fptu.exe202.signify.signifybe.features.user.api.dto.PageResponse;
import fptu.exe202.signify.signifybe.features.user.application.FriendService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class FriendController {
    private final FriendService friends;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<FriendSearchResponse>>> search(
            @AuthenticationPrincipal CurrentUser current,
            @RequestParam("q")
            @Size(max = UserValidation.SEARCH_MAX_LENGTH)
            @Pattern(regexp = UserValidation.NON_BLANK_REGEX) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(UserValidation.PAGE_MAX_SIZE) int size) {
        return ok(PageResponse.of(friends.search(current.userId(), query, page, size)));
    }

    @PostMapping("/requests/{targetUserId}")
    public ResponseEntity<ApiResponse<FriendRelationshipResponse>> send(
            @AuthenticationPrincipal CurrentUser current,
            @PathVariable @Positive long targetUserId) {
        return ok(friends.send(current.userId(), targetUserId));
    }

    @GetMapping("/requests/incoming")
    public ResponseEntity<ApiResponse<PageResponse<FriendRequestResponse>>> incoming(
            @AuthenticationPrincipal CurrentUser current,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(UserValidation.PAGE_MAX_SIZE) int size) {
        return ok(PageResponse.of(friends.incoming(current.userId(), page, size)));
    }

    @GetMapping("/requests/outgoing")
    public ResponseEntity<ApiResponse<PageResponse<FriendRequestResponse>>> outgoing(
            @AuthenticationPrincipal CurrentUser current,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(UserValidation.PAGE_MAX_SIZE) int size) {
        return ok(PageResponse.of(friends.outgoing(current.userId(), page, size)));
    }

    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<ApiResponse<FriendResponse>> accept(
            @AuthenticationPrincipal CurrentUser current,
            @PathVariable @Positive long requestId) {
        return ok(friends.accept(current.userId(), requestId));
    }

    @PostMapping("/requests/{requestId}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
            @AuthenticationPrincipal CurrentUser current,
            @PathVariable @Positive long requestId) {
        friends.reject(current.userId(), requestId);
        return ok(null);
    }

    @DeleteMapping("/requests/{requestId}")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @AuthenticationPrincipal CurrentUser current,
            @PathVariable @Positive long requestId) {
        friends.cancel(current.userId(), requestId);
        return ok(null);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<FriendResponse>>> list(
            @AuthenticationPrincipal CurrentUser current,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(UserValidation.PAGE_MAX_SIZE) int size) {
        return ok(PageResponse.of(friends.friends(current.userId(), page, size)));
    }

    @DeleteMapping("/{friendUserId}")
    public ResponseEntity<ApiResponse<Void>> unfriend(
            @AuthenticationPrincipal CurrentUser current,
            @PathVariable @Positive long friendUserId) {
        friends.unfriend(current.userId(), friendUserId);
        return ok(null);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
    }
}
