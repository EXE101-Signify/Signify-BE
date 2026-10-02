package fptu.exe202.signify.signifybe.features.user.api;

import fptu.exe202.signify.signifybe.config.SecurityConfig;
import fptu.exe202.signify.signifybe.features.auth.application.AccountAccessService;
import fptu.exe202.signify.signifybe.features.auth.application.JwtService;
import fptu.exe202.signify.signifybe.features.auth.domain.Account;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.security.ApiSecurityErrorHandler;
import fptu.exe202.signify.signifybe.features.user.application.UserManagementService;
import fptu.exe202.signify.signifybe.features.user.domain.ManagedUser;
import fptu.exe202.signify.signifybe.features.user.domain.User;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminUserController.class)
@Import(SecurityConfig.class)
class AdminUserControllerSecurityTest {
    @Autowired MockMvc mvc;

    @MockitoBean UserManagementService users;
    @MockitoBean JwtService jwtService;
    @MockitoBean AccountAccessService accountAccess;
    @MockitoBean ApiSecurityErrorHandler securityErrors;

    @BeforeEach
    void configureSecurityErrors() throws Exception {
        doAnswer(invocation -> {
            HttpServletResponse response = invocation.getArgument(1);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return null;
        }).when(securityErrors).commence(any(), any(), any());
        doAnswer(invocation -> {
            HttpServletResponse response = invocation.getArgument(1);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }).when(securityErrors).handle(any(), any(), any());
    }

    @Test
    void anonymousCannotBanOrUnban() throws Exception {
        mvc.perform(delete("/api/admin/users/2"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/admin/users/2/unban"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(users);
    }

    @Test
    void regularUserCannotBanOrUnban() throws Exception {
        authenticate("user-token", 7L, Role.USER);

        mvc.perform(delete("/api/admin/users/2").header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/users/2/unban").header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(users);
    }

    @Test
    void adminCanBan() throws Exception {
        authenticate("admin-token", 1L, Role.ADMIN);
        ManagedUser banned = managedUser(2L, true);
        when(users.ban(1L, 2L, null)).thenReturn(banned);

        mvc.perform(delete("/api/admin/users/2").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.status").value("BANNED"))
                .andExpect(jsonPath("$.data.deletedAt").value(2_000L));

        verify(users).ban(1L, 2L, null);
    }

    @Test
    void adminCanUnban() throws Exception {
        authenticate("admin-token", 1L, Role.ADMIN);
        ManagedUser active = managedUser(2L, false);
        when(users.unban(1L, 2L, null)).thenReturn(active);

        mvc.perform(patch("/api/admin/users/2/unban").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.deletedAt").doesNotExist());

        verify(users).unban(1L, 2L, null);
    }

    private void authenticate(String token, long userId, Role role) {
        CurrentUser currentUser = new CurrentUser(userId, role);
        when(jwtService.validateAccessToken(token)).thenReturn(currentUser);
        when(accountAccess.requireActiveUser(userId)).thenReturn(currentUser);
    }

    private ManagedUser managedUser(long userId, boolean banned) {
        User user = new User("user@example.com", "Test", "User", 1_000L);
        user.setId(userId);
        Account account = new Account(userId, "user" + userId, "hash", 1_000L);
        if (banned) {
            user.ban(2_000L);
            account.ban(2_000L);
        }
        return ManagedUser.of(user, account);
    }
}
