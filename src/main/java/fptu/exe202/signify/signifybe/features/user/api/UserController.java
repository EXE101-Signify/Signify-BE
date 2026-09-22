package fptu.exe202.signify.signifybe.features.user.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.signifybe.features.auth.api.dto.AuthResponse;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.user.api.dto.RegisterRequest;
import fptu.exe202.signify.signifybe.features.user.api.dto.UserResponse;
import fptu.exe202.signify.signifybe.features.user.application.UserService;
import fptu.exe202.signify.signifybe.features.user.application.UserManagementService;
import fptu.exe202.signify.signifybe.features.user.api.dto.UpdateProfileRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import fptu.exe202.signify.signifybe.features.auth.mapper.AuthMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import fptu.exe202.signify.signifybe.features.auth.domain.SessionMetadata;
import fptu.exe202.signify.signifybe.features.user.mapper.UserMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserController {

    UserService userService;
    UserManagementService userManagementService;
    UserMapper userMapper;
    AuthMapper authMapper;

    @PostMapping(value = "/register", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<AuthResponse>> registerWithAvatar(
            @Valid @ModelAttribute RegisterRequest request, HttpServletRequest http) {
        var result = userService.register(request.username(), request.password(), request.email(),
                request.firstName(), request.lastName(), request.avatar(), metadata(http, null));
        return noStore(ApiResponse.success("Account registered successfully", authMapper.toAuthResponse(result)));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal CurrentUser user) {
        return noStore(ApiResponse.success(userMapper.toResponse(userService.me(user.userId()))));
    }

    @PatchMapping("/me")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody UpdateProfileRequest request) {
        return noStore(ApiResponse.success("Profile updated successfully",
                userMapper.toResponse(userManagementService.updateOwn(user.userId(), request))));
    }
    private SessionMetadata metadata(HttpServletRequest request, String deviceName) {
        return new SessionMetadata(deviceName, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
    private <T> ResponseEntity<ApiResponse<T>> noStore(ApiResponse<T> body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
