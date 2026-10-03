package fptu.exe202.signify.signifybe.features.user.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.user.api.dto.BlockedUserResponse;
import fptu.exe202.signify.signifybe.features.user.application.BlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class UserBlockController {
    private final BlockService blocks;

    @PostMapping("/{userId}/block")
    public ResponseEntity<ApiResponse<BlockedUserResponse>> block(@AuthenticationPrincipal CurrentUser current,
                                                                  @PathVariable long userId) {
        return noStore(ApiResponse.success("User blocked successfully", blocks.block(current.userId(), userId)));
    }

    @DeleteMapping("/{userId}/block")
    public ResponseEntity<ApiResponse<Void>> unblock(@AuthenticationPrincipal CurrentUser current,
                                                     @PathVariable long userId) {
        blocks.unblock(current.userId(), userId);
        return noStore(ApiResponse.success("User unblocked successfully", null));
    }

    @GetMapping("/blocked")
    public ResponseEntity<ApiResponse<List<BlockedUserResponse>>> listBlocked(@AuthenticationPrincipal CurrentUser current) {
        return noStore(ApiResponse.success(blocks.listBlocked(current.userId())));
    }

    private <T> ResponseEntity<ApiResponse<T>> noStore(ApiResponse<T> body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
