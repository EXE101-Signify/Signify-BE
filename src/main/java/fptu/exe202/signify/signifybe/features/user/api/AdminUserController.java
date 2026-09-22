package fptu.exe202.signify.signifybe.features.user.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.user.api.dto.UpdateProfileRequest;
import fptu.exe202.signify.signifybe.features.user.application.UserManagementService;
import fptu.exe202.signify.signifybe.features.user.domain.ManagedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final UserManagementService users;

    @GetMapping
    public ResponseEntity<ApiResponse<UserPage>> list(
            @RequestParam(defaultValue = "") @Size(max = 150) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok(UserPage.of(users.list(search, page, size)));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<ManagedUser>> get(@PathVariable @Positive long userId) {
        return ok(users.get(userId));
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<ApiResponse<ManagedUser>> update(@PathVariable @Positive long userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ok(users.updateByAdmin(userId, request));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<ManagedUser>> ban(@AuthenticationPrincipal CurrentUser actor,
            @PathVariable @Positive long userId) {
        return ok(users.ban(actor.userId(), userId));
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
    }

    public record UserPage(java.util.List<ManagedUser> content, int page, int size, long totalElements, int totalPages) {
        static UserPage of(Page<ManagedUser> page) {
            return new UserPage(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}
