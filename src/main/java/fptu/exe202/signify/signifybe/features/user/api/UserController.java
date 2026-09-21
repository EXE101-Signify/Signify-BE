package fptu.exe202.signify.signifybe.features.user.api;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import fptu.exe202.signify.signifybe.features.auth.api.dto.AuthResponse;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.user.api.dto.RegisterRequest;
import fptu.exe202.signify.signifybe.features.user.api.dto.UserResponse;
import fptu.exe202.signify.signifybe.features.user.application.UserService;
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

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request,
                                                              HttpServletRequest http) {
        var result = userService.register(request.username(), request.password(), request.email(),
                request.firstName(), request.lastName(), metadata(http, null));
        return noStore(ApiResponse.success("Account registered successfully", AuthResponse.from(result)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal CurrentUser user) {
        return noStore(ApiResponse.success(UserMapper.toResponse(userService.me(user.userId()))));
    }
    private SessionMetadata metadata(HttpServletRequest request, String deviceName) {
        return new SessionMetadata(deviceName, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
    private <T> ResponseEntity<ApiResponse<T>> noStore(ApiResponse<T> body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
