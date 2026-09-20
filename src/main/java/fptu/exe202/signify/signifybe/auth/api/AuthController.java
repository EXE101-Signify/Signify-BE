package fptu.exe202.signify.signifybe.auth.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.signifybe.auth.api.dto.*;
import fptu.exe202.signify.signifybe.auth.application.AuthService;
import fptu.exe202.signify.signifybe.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.auth.domain.SessionMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request,
                                                             HttpServletRequest http) {
        var result = authService.register(request.username(), request.password(), request.email(),
                request.firstName(), request.lastName(), metadata(http, null));
        return noStore(ApiResponse.success("Account registered successfully", AuthResponse.from(result)));
    }

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

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal CurrentUser user) {
        return noStore(ApiResponse.success(UserResponse.from(authService.me(user.userId()))));
    }

    private SessionMetadata metadata(HttpServletRequest request, String deviceName) {
        // Intentionally ignore X-Forwarded-For; no trusted proxy configuration exists in this project.
        return new SessionMetadata(deviceName, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    private <T> ResponseEntity<ApiResponse<T>> noStore(ApiResponse<T> body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
