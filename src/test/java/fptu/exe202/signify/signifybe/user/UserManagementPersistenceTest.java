package fptu.exe202.signify.signifybe.user;

import fptu.exe202.signify.signifybe.features.auth.application.*;
import fptu.exe202.signify.signifybe.features.auth.domain.*;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import fptu.exe202.signify.signifybe.features.auth.infrastructure.persistence.*;
import fptu.exe202.signify.signifybe.features.user.api.dto.UpdateProfileRequest;
import fptu.exe202.signify.signifybe.features.user.application.UserManagementService;
import fptu.exe202.signify.signifybe.features.user.domain.User;
import fptu.exe202.signify.signifybe.features.user.domain.exception.UserException;
import fptu.exe202.signify.signifybe.features.user.infrastructure.persistence.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.jpa.show-sql=false"})
@Import({UserManagementService.class, AccountAccessService.class, JpaUserPersistence.class,
        JpaAuthPersistence.class, AuthService.class, TokenService.class, JwtService.class,
        UserManagementPersistenceTest.Config.class})
class UserManagementPersistenceTest {
    @Autowired UserManagementService management;
    @Autowired AccountAccessService access;
    @Autowired JpaUserRepository users;
    @Autowired JpaAccountRepository accounts;
    @Autowired JpaUserSessionRepository sessions;
    @Autowired EntityManager em;
    @Autowired TokenService tokens;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder encoder;
    long adminId;
    long userId;
    private static final SessionMetadata METADATA = new SessionMetadata(null, null, null);

    @BeforeEach void setup() {
        adminId = create("admin", "admin@example.com", Role.ADMIN);
        userId = create("user", "user@example.com", Role.USER);
        authenticate(userId, Role.USER);
    }

    long create(String username, String email, Role role) {
        User user = users.saveAndFlush(new User(email, "Old", "Name", 123L));
        Account account = new Account(user.getId(), username, encoder.encode("Password123!"), 123L);
        ReflectionTestUtils.setField(account, "role", role);
        accounts.saveAndFlush(account);
        return user.getId();
    }

    void authenticate(long id, Role role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new CurrentUser(id, role), null, List.of(new SimpleGrantedAuthority(role.authority()))));
    }

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void repeatedProfileUpdatesKeepSameRowIdentityAndCreationTimestamp() {
        users.findById(userId).orElseThrow().setEmailVerified(true);
        em.flush(); em.clear();
        var first = management.updateOwn(userId, new UpdateProfileRequest(null, "New", null, null));
        em.flush(); em.clear();
        var second = management.updateOwn(userId, new UpdateProfileRequest("changed@example.com", null, "Surname", "https://example.com/avatar.png"));
        em.flush(); em.clear();
        User persisted = users.findById(userId).orElseThrow();
        assertThat(users.count()).isEqualTo(2);
        assertThat(accounts.count()).isEqualTo(2);
        assertThat(first.userId()).isEqualTo(userId);
        assertThat(second.userId()).isEqualTo(userId);
        assertThat(first.emailVerified()).isTrue();
        assertThat(persisted.getFullName()).isEqualTo("New Surname");
        assertThat(persisted.getCreatedAt()).isEqualTo(123L);
        assertThat(persisted.getUpdatedAt()).isGreaterThan(123L);
        assertThat(persisted.isUpdatedProfile()).isTrue();
        assertThat(persisted.isEmailVerified()).isFalse();
        assertThat(accounts.findById(userId).orElseThrow().getUsername()).isEqualTo("user");
        assertThat(accounts.findById(userId).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test void missingTargetDoesNotInsertUser() {
        authenticate(adminId, Role.ADMIN);
        assertThatThrownBy(() -> management.updateByAdmin(999999, new UpdateProfileRequest(null, "New", null, null)))
                .isInstanceOf(UserException.class);
        assertThat(users.count()).isEqualTo(2);
    }

    @Test void usersCannotReadManageOrBanOtherUsersEvenViaService() {
        assertThatThrownBy(() -> management.list("", 0, 20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> management.get(adminId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> management.updateByAdmin(adminId, new UpdateProfileRequest(null, "New", null, null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> management.ban(userId, adminId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> management.updateOwn(adminId, new UpdateProfileRequest(null, "New", null, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test void banSoftDeletesRevokesSessionsAndBlocksAccessLoginAndRefresh() {
        var pair = tokens.issueTokens(accounts.findById(userId).orElseThrow(), METADATA);
        authenticate(adminId, Role.ADMIN);
        var banned = management.ban(adminId, userId);
        em.flush(); em.clear();
        assertThat(users.count()).isEqualTo(2);
        assertThat(users.findById(userId).orElseThrow().isDeleted()).isTrue();
        assertThat(accounts.findById(userId).orElseThrow().getStatus()).isEqualTo("BANNED");
        assertThat(sessions.findAll()).hasSize(1).allMatch(session -> session.getRevokedAt() != null);
        assertThatThrownBy(() -> access.requireActiveUser(userId)).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> auth.login("user", "Password123!", METADATA)).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> auth.refresh(pair.refreshToken(), METADATA)).isInstanceOf(AuthException.class);
        assertThat(management.ban(adminId, userId).deletedAt()).isEqualTo(banned.deletedAt());
        assertThatThrownBy(() -> management.updateByAdmin(userId, new UpdateProfileRequest(null, "New", null, null)))
                .isInstanceOf(AuthException.class);
    }

    @Test void adminCannotBanSelfButCanUpdateAndSearchUsers() {
        authenticate(adminId, Role.ADMIN);
        assertThatThrownBy(() -> management.ban(adminId, adminId)).isInstanceOf(UserException.class);
        var updated = management.updateByAdmin(userId, new UpdateProfileRequest(null, "Managed", null, null));
        assertThat(updated.profile().firstName()).isEqualTo("Managed");
        em.flush(); em.clear();
        assertThat(management.list("USER", 0, 1).getTotalElements()).isEqualTo(1);
        assertThat(management.list("", 0, 1).getTotalElements()).isEqualTo(2);
        assertThat(management.get(userId).profile().userId()).isEqualTo(userId);
    }

    @Test void unchangedEmailAllowedButAnotherUsersEmailRejected() {
        management.updateOwn(userId, new UpdateProfileRequest("user@example.com", null, null, null));
        assertThatThrownBy(() -> management.updateOwn(userId, new UpdateProfileRequest("admin@example.com", null, null, null)))
                .isInstanceOf(fptu.exe202.signify.apiresponse.exception.ConflictException.class);
        assertThat(users.findById(userId).orElseThrow().getEmail()).isEqualTo("user@example.com");
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Config {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(4); }
        @Bean JwtProperties properties() {
            return new JwtProperties("test-signing-secret-with-at-least-32-bytes", Base64.getEncoder().encodeToString(new byte[32]),
                    900000, 604800000, "test-issuer", "test-audience");
        }
    }
}
