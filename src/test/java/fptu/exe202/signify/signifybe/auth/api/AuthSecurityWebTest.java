package fptu.exe202.signify.signifybe.auth.api;

import fptu.exe202.signify.apiresponse.config.ApiResponseAutoConfiguration;
import fptu.exe202.signify.signifybe.features.auth.api.AuthController;
import fptu.exe202.signify.signifybe.features.auth.application.*;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.auth.domain.SessionMetadata;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import fptu.exe202.signify.signifybe.features.user.api.UserController;
import fptu.exe202.signify.signifybe.features.user.application.UserService;
import fptu.exe202.signify.signifybe.features.user.domain.exception.UserException;
import fptu.exe202.signify.signifybe.features.user.domain.UserProfile;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import fptu.exe202.signify.signifybe.features.auth.infrastructure.security.AuthConfiguration;
import fptu.exe202.signify.signifybe.features.auth.mapper.AuthMapperImpl;
import fptu.exe202.signify.signifybe.config.CorsConfig;
import fptu.exe202.signify.signifybe.config.SecurityConfig;
import fptu.exe202.signify.signifybe.features.security.ApiSecurityErrorHandler;
import fptu.exe202.signify.signifybe.features.storage.api.StorageController;
import fptu.exe202.signify.signifybe.features.storage.application.StorageService;
import fptu.exe202.signify.signifybe.features.storage.mapper.StorageMapperImpl;
import fptu.exe202.signify.signifybe.features.user.mapper.UserMapperImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ImportAutoConfiguration(ApiResponseAutoConfiguration.class)
@WebMvcTest(controllers = {AuthController.class, UserController.class, StorageController.class, AuthSecurityWebTest.RoleProbe.class})
@Import({SecurityConfig.class, CorsConfig.class, JwtService.class, AuthConfiguration.class, ApiSecurityErrorHandler.class,
        AuthMapperImpl.class, UserMapperImpl.class, StorageMapperImpl.class, AuthSecurityWebTest.RoleProbe.class})
class AuthSecurityWebTest {
    private static final String ENCRYPTION_KEY = java.util.Base64.getEncoder()
            .encodeToString(io.jsonwebtoken.Jwts.ENC.A256GCM.key().build().getEncoded());
    private static final String SECRET = UUID.randomUUID() + "-" + UUID.randomUUID();
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @MockitoBean private AuthService authService;
    @MockitoBean private UserService userService;
    @MockitoBean private StorageService storageService;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> SECRET);
        registry.add("app.jwt.refresh-encryption-key", () -> ENCRYPTION_KEY);
    }

    private String bearer(Role role) { return "Bearer " + jwt.createAccessToken(42, role).value(); }
    private UserProfile profile() {
        return new UserProfile(42, "test-user", Role.USER, null, "Test", "User", "Test User", null, false);
    }
    private AuthResult authResult() {
        var access = jwt.createAccessToken(42, Role.USER);
        var refresh = jwt.createRefreshToken(42);
        return new AuthResult(profile(), new TokenPair(access.value(), refresh.value(), access.expiresAt(), refresh.expiresAt()));
    }

    @Test void protectedStorageEndpointRequiresTokenAndReturnsStandardEnvelope() throws Exception {
        mvc.perform(get("/api/storage/images/url").param("key", "unused"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(401)).andExpect(jsonPath("$.path").value("/api/storage/images/url"))
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        verifyNoInteractions(storageService);
    }

    @Test void validAccessTokenAuthenticatesExistingStorageRoute() throws Exception {
        when(storageService.generateUrl("image-key")).thenReturn("https://images.example/image");
        mvc.perform(get("/api/storage/images/url").param("key", "image-key").header("Authorization", bearer(Role.USER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.url").value("https://images.example/image"));
        verifyNoInteractions(authService);
    }

    @Test void invalidExpiredAndRefreshBearerTokensAreRejected() throws Exception {
        var expired = new JwtService(new JwtProperties(SECRET, ENCRYPTION_KEY, 1000, 604800000),
                Clock.fixed(Instant.now().minusSeconds(120), ZoneOffset.UTC)).createAccessToken(42, Role.USER).value();
        for (String token : new String[]{"invalid", expired, jwt.createRefreshToken(42).value()}) {
            mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        }
        verifyNoInteractions(authService);
    }

    @Test void roleAuthorizationUsesRolePrefixConsistently() throws Exception {
        mvc.perform(get("/test/admin").header("Authorization", bearer(Role.USER)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/test/admin").header("Authorization", bearer(Role.ADMIN))).andExpect(status().isOk());
    }

    @Test void registerIsPublicValidatedAndUsesStandardResponse() throws Exception {
        when(userService.register(eq("test-user"), eq("password123"), isNull(), isNull(), isNull(), any()))
                .thenReturn(authResult());
        mvc.perform(post("/api/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-user\",\"password\":\"password123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value(200)).andExpect(jsonPath("$.data.user.role").value("USER"))
                .andExpect(jsonPath("$.data.tokens.tokenType").value("Bearer"))
                .andExpect(encryptedRefresh("$.data.tokens.refreshToken"))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test void validationErrorsUseExistingGlobalHandler() throws Exception {
        mvc.perform(post("/api/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"short\",\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.username").exists()).andExpect(jsonPath("$.errors.password").exists());
        verifyNoInteractions(authService);
    }

    @Test void loginUsesSocketAddressRatherThanForwardedIp() throws Exception {
        when(authService.login(anyString(), anyString(), any())).thenReturn(authResult());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-user\",\"password\":\"password123\",\"deviceName\":\"Laptop\"}")
                        .header("X-Forwarded-For", "1.2.3.4").header("User-Agent", "test-agent")
                        .with(request -> { request.setRemoteAddr("127.0.0.2"); return request; }))
                .andExpect(status().isOk()).andExpect(encryptedRefresh("$.data.tokens.refreshToken"));
        verify(authService).login("test-user", "password123", new SessionMetadata("Laptop", "127.0.0.2", "test-agent"));
    }

    @Test void duplicateAndInvalidCredentialsUseExistingExceptionEnvelope() throws Exception {
        when(userService.register(anyString(), anyString(), any(), any(), any(), any())).thenThrow(UserException.usernameTaken());
        mvc.perform(post("/api/users/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-user\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Username already exists"));
        when(authService.login(anyString(), anyString(), any())).thenThrow(AuthException.invalidCredentials());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"test-user\",\"password\":\"incorrect\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test void refreshIsPublicAndDoesNotRequireAccessToken() throws Exception {
        when(authService.refresh(eq("refresh-token"), any())).thenReturn(authResult().tokens());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(encryptedRefresh("$.data.refreshToken"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test void meAndBothLogoutEndpointsAreProtected() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"token\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout-all")).andExpect(status().isUnauthorized());
        verifyNoInteractions(authService);
    }

    @Test void authenticatedRequestsUseJwtIdentity() throws Exception {
        when(userService.me(42)).thenReturn(profile());
        mvc.perform(get("/api/users/me").header("Authorization", bearer(Role.USER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(42));
        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(Role.USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"token\",\"userId\":999}"))
                .andExpect(status().isOk());
        verify(authService).logout(42, "token");
        mvc.perform(post("/api/auth/logout-all").header("Authorization", bearer(Role.USER)))
                .andExpect(status().isOk());
        verify(authService).logoutAll(42);
    }

    @Test void swaggerIsStillPublicAndCorsPreflightWorks() throws Exception {
        // The slice does not include springdoc's controller, so a public route reaches MVC's 404.
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(options("/api/storage/images").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    private org.springframework.test.web.servlet.ResultMatcher encryptedRefresh(String path) {
        return result -> {
            String token = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path);
            assertThat(token.split("\\.", -1)).hasSize(5);
            assertThat(jwt.validateRefreshToken(token)).isEqualTo(42);
        };
    }
    @RestController
    static class RoleProbe {
        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/test/admin") public String admin() { return "ok"; }
    }
}
