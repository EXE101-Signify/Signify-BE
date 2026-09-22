package fptu.exe202.signify.signifybe.user;

import fptu.exe202.signify.apiresponse.config.ApiResponseAutoConfiguration;
import fptu.exe202.signify.signifybe.config.SecurityConfig;
import fptu.exe202.signify.signifybe.features.auth.application.*;
import fptu.exe202.signify.signifybe.features.auth.application.port.out.AccountRepository;
import fptu.exe202.signify.signifybe.features.auth.domain.*;
import fptu.exe202.signify.signifybe.features.auth.infrastructure.security.AuthConfiguration;
import fptu.exe202.signify.signifybe.features.auth.mapper.AuthMapperImpl;
import fptu.exe202.signify.signifybe.features.security.ApiSecurityErrorHandler;
import fptu.exe202.signify.signifybe.features.user.api.*;
import fptu.exe202.signify.signifybe.features.user.api.dto.UpdateProfileRequest;
import fptu.exe202.signify.signifybe.features.user.application.*;
import fptu.exe202.signify.signifybe.features.user.application.port.out.UserRepository;
import fptu.exe202.signify.signifybe.features.user.domain.*;
import fptu.exe202.signify.signifybe.features.user.mapper.UserMapperImpl;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {UserController.class, AdminUserController.class})
@ImportAutoConfiguration(ApiResponseAutoConfiguration.class)
@Import({SecurityConfig.class, JwtService.class, AccountAccessService.class, AuthConfiguration.class,
        ApiSecurityErrorHandler.class, UserMapperImpl.class, AuthMapperImpl.class})
class UserManagementWebTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean UserService userService;
    @MockitoBean UserManagementService management;
    @MockitoBean AccountRepository accounts;
    @MockitoBean UserRepository users;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> "test-signing-secret-with-at-least-32-bytes");
        registry.add("app.jwt.refresh-encryption-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("app.jwt.issuer", () -> "test-issuer");
        registry.add("app.jwt.audience", () -> "test-client");
    }

    Account account;
    User user;
    @BeforeEach void setup() {
        account = new Account(42, "huybg", "never-expose-password-hash", 1L);
        user = new User("user@example.com", "Huy", "Bui", 1L);
        user.setId(42L);
        when(accounts.findAccountByUserId(42)).thenReturn(Optional.of(account));
        when(users.findUserById(42)).thenReturn(Optional.of(user));
    }

    String token(Role role) { return "Bearer " + jwt.createAccessToken(42, "huybg", role).value(); }
    void admin() { ReflectionTestUtils.setField(account, "role", Role.ADMIN); }

    @Test void allNewRoutesRequireAuthentication() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users/7")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/users/7")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/admin/users/7").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/users/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(management);
    }

    @Test void userCannotUseAnyAdminActionEvenWithOldAdminToken() throws Exception {
        String oldAdminToken = token(Role.ADMIN);
        mvc.perform(get("/api/admin/users").header("Authorization", oldAdminToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users/7").header("Authorization", oldAdminToken)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/users/7").header("Authorization", oldAdminToken)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/users/7").header("Authorization", oldAdminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(management);
    }

    @Test void adminCanListInspectUpdateAndBanUsersWithoutExposingCredentials() throws Exception {
        admin();
        var profile = new ManagedUser(UserProfile.of(user, account), "ACTIVE", 1L, 1L, null);
        when(management.list("", 0, 20)).thenReturn(new PageImpl<>(List.of(profile)));
        when(management.get(7)).thenReturn(profile);
        when(management.updateByAdmin(eq(7L), any())).thenReturn(profile);
        when(management.ban(42, 7)).thenReturn(profile);
        String bearer = token(Role.ADMIN);
        mvc.perform(get("/api/admin/users").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].profile.userId").value(42))
                .andExpect(jsonPath("$.data.content[0].profile.passwordHash").doesNotExist());
        mvc.perform(get("/api/admin/users/7").header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(patch("/api/admin/users/7").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Changed\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(delete("/api/admin/users/7").header("Authorization", bearer)).andExpect(status().isOk());
        verify(management).ban(42, 7);
    }

    @Test void profileUpdateUsesAuthenticatedIdentityAndIgnoresPrivilegedFields() throws Exception {
        when(management.updateOwn(eq(42L), any())).thenReturn(UserProfile.of(user, account));
        mvc.perform(patch("/api/users/me").header("Authorization", token(Role.USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":999,\"id\":999,\"role\":\"ADMIN\",\"status\":\"ACTIVE\",\"username\":\"hacked\",\"firstName\":\"New\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(42))
                .andExpect(jsonPath("$.data.role").value("USER"));
        verify(management).updateOwn(42, new UpdateProfileRequest(null, "New", null, null));
    }

    @Test void bannedAndDeletedUsersCannotUseExistingTokens() throws Exception {
        String bearer = token(Role.USER);
        account.ban(System.currentTimeMillis());
        mvc.perform(patch("/api/users/me").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        ReflectionTestUtils.setField(account, "status", "ACTIVE");
        user.setDeletedAt(System.currentTimeMillis());
        mvc.perform(get("/api/users/me").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        verifyNoInteractions(management, userService);
    }

    @Test void invalidProfileFieldsAndPaginationAreRejected() throws Exception {
        String bearer = token(Role.USER);
        for (String body : List.of("{\"email\":\"bad\"}", "{\"email\":\"\"}", "{\"avatar\":\"javascript:alert(1)\"}",
                "{\"firstName\":\"" + "a".repeat(101) + "\"}")) {
            mvc.perform(patch("/api/users/me").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        admin();
        mvc.perform(get("/api/admin/users").param("size", "101").header("Authorization", token(Role.ADMIN)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/users").param("page", "-1").header("Authorization", token(Role.ADMIN)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/users/0").header("Authorization", token(Role.ADMIN)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/users/invalid-id").header("Authorization", token(Role.ADMIN)))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/users/7").header("Authorization", token(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"bad\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(management);
    }
}
