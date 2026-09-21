package fptu.exe202.signify.signifybe.features.auth.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.signifybe.auth.api.dto.*;
import fptu.exe202.signify.signifybe.features.auth.api.dto.*;
import fptu.exe202.signify.signifybe.features.auth.application.AuthService;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.SessionMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthController {
    AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                           HttpServletRequest http) {
        return noStore(ApiResponse.success("Login successful", AuthResponse.from(
                authService.login(request.username(), request.password(), metadata(http, request.deviceName())))));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                                              HttpServletRequest http) {
        return noStore(ApiResponse.success(TokenResponse.from(authService.refresh(request.refreshToken(), metadata(http, null)))));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody LogoutRequest request) {
        authService.logout(user.userId(), request.refreshToken());
        return ApiResponse.success("Logged out successfully", null);
    }

    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutAll(@AuthenticationPrincipal CurrentUser user) {
        authService.logoutAll(user.userId());
        return ApiResponse.success("All sessions revoked", null);
    }

    private SessionMetadata metadata(HttpServletRequest request, String deviceName) {
        // Intentionally ignore X-Forwarded-For; no trusted proxy configuration exists in this project.
        return new SessionMetadata(deviceName, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    private <T> ResponseEntity<ApiResponse<T>> noStore(ApiResponse<T> body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
