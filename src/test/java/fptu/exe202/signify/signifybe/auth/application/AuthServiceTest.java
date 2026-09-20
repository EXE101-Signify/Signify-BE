package fptu.exe202.signify.signifybe.auth.application;

import fptu.exe202.signify.signifybe.auth.application.port.out.*;
import fptu.exe202.signify.signifybe.auth.domain.*;
import fptu.exe202.signify.signifybe.auth.domain.exception.AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private static final String ENCRYPTION_KEY = java.util.Base64.getEncoder()
            .encodeToString(io.jsonwebtoken.Jwts.ENC.A256GCM.key().build().getEncoded());
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserSessionRepository sessions = mock(UserSessionRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-20T00:00:00Z"), ZoneOffset.UTC);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final JwtService jwt = new JwtService(new JwtProperties(UUID.randomUUID() + "-" + UUID.randomUUID(), ENCRYPTION_KEY,
            900000, 604800000), clock);
    private final SessionMetadata metadata = new SessionMetadata("browser", "127.0.0.1", "test-agent");
    private AuthService service;
    private User user;
    private Account account;

    @BeforeEach void setUp() {
        service = new AuthService(accounts, users, sessions, encoder, jwt, clock);
        user = new User(null, "Test", "User", clock.millis());
        ReflectionTestUtils.setField(user, "id", 42L);
        account = new Account(42, "test-user", encoder.encode("password123"), clock.millis());
    }

    private void activeAccount() {
        when(accounts.lockAccount(42)).thenReturn(Optional.of(account));
        when(users.findUserById(42)).thenReturn(Optional.of(user));
    }

    @Test void bcryptDoesNotStorePlaintextAndUsesSalt() {
        String first = encoder.encode("password123");
        assertThat(first).isNotEqualTo("password123").isNotEqualTo(encoder.encode("password123"));
        assertThat(encoder.matches("password123", first)).isTrue();
        assertThat(encoder.matches("incorrect", first)).isFalse();
    }

    @Test void registrationCreatesUserAccountAndHashedSession() {
        when(users.insertUser(any())).thenReturn(user);
        when(accounts.insertAccount(any())).thenAnswer(call -> call.getArgument(0));
        var result = service.register("test-user", "password123", null, "Test", "User", metadata);
        var created = ArgumentCaptor.forClass(Account.class);
        verify(accounts).insertAccount(created.capture());
        assertThat(encoder.matches("password123", created.getValue().getPasswordHash())).isTrue();
        assertThat(created.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(created.getValue().isActive()).isTrue();
        var session = ArgumentCaptor.forClass(UserSession.class);
        verify(sessions).saveSession(session.capture());
        assertThat(session.getValue().getRefreshTokenHash()).isEqualTo(jwt.hashRefreshToken(result.tokens().refreshToken()));
        assertThat(session.getValue().getRefreshTokenHash()).doesNotContain(result.tokens().refreshToken());
        assertThat(session.getValue().getIpAddress()).isEqualTo("127.0.0.1");
    }

    @Test void duplicateUsernameDoesNotCreateRecords() {
        when(accounts.usernameExists("test-user")).thenReturn(true);
        assertThatThrownBy(() -> service.register("test-user", "password123", null, null, null, metadata))
                .isInstanceOf(AuthException.class).hasMessage("Username already exists");
        verifyNoInteractions(users, sessions);
    }

    @Test void rejectsPasswordsExceedingBcryptByteLimit() {
        assertThatThrownBy(() -> service.register("test-user", "\u00e9".repeat(37), null, null, null, metadata))
                .isInstanceOf(AuthException.class);
        verifyNoInteractions(users, accounts, sessions);
    }

    @Test void loginCreatesTokens() {
        activeAccount();
        when(accounts.lockAccountByUsername("test-user")).thenReturn(Optional.of(account));
        var result = service.login("test-user", "password123", metadata);
        assertThat(jwt.validateAccessToken(result.tokens().accessToken()).userId()).isEqualTo(42);
        assertThat(result.user().username()).isEqualTo("test-user");
        verify(sessions).saveSession(any());
    }

    @Test void invalidPasswordAndMissingAccountUseSameError() {
        when(accounts.lockAccountByUsername("test-user")).thenReturn(Optional.of(account));
        assertThatThrownBy(() -> service.login("test-user", "incorrect", metadata))
                .isInstanceOf(AuthException.class).hasMessage("Invalid username or password");
        assertThatThrownBy(() -> service.login("missing", "incorrect", metadata))
                .isInstanceOf(AuthException.class).hasMessage("Invalid username or password");
        verifyNoInteractions(sessions);
    }

    @Test void disablesLoginForInactiveAccountOrDeletedUser() {
        activeAccount();
        when(accounts.lockAccountByUsername("test-user")).thenReturn(Optional.of(account));
        ReflectionTestUtils.setField(account, "status", "SUSPENDED");
        assertThatThrownBy(() -> service.login("test-user", "password123", metadata)).isInstanceOf(AuthException.class)
                .hasMessage("Account is not active");
        ReflectionTestUtils.setField(account, "status", "ACTIVE");
        ReflectionTestUtils.setField(user, "deletedAt", clock.millis());
        assertThatThrownBy(() -> service.login("test-user", "password123", metadata)).isInstanceOf(AuthException.class);
        verifyNoInteractions(sessions);
    }

    @Test void rejectsOauthOnlyPasswordLogin() {
        ReflectionTestUtils.setField(account, "passwordHash", null);
        when(accounts.lockAccountByUsername("test-user")).thenReturn(Optional.of(account));
        assertThatThrownBy(() -> service.login("test-user", "password123", metadata)).isInstanceOf(AuthException.class);
        verifyNoInteractions(sessions);
    }

    @Test void refreshRotatesTokenAndRevokesOldSession() {
        activeAccount();
        String refresh = jwt.createRefreshToken(42).value();
        UserSession old = new UserSession(42, jwt.hashRefreshToken(refresh), metadata, clock.millis(), clock.millis() + 100000);
        when(sessions.lockSessionByHash(old.getRefreshTokenHash())).thenReturn(Optional.of(old));
        var result = service.refresh(refresh, metadata);
        assertThat(result.refreshToken()).isNotEqualTo(refresh);
        assertThat(old.getRevokedAt()).isEqualTo(clock.millis());
        assertThat(jwt.validateAccessToken(result.accessToken()).userId()).isEqualTo(42);
        var saved = ArgumentCaptor.forClass(UserSession.class);
        verify(sessions, times(2)).saveSession(saved.capture());
        assertThat(saved.getAllValues().get(1).getRefreshTokenHash()).isEqualTo(jwt.hashRefreshToken(result.refreshToken()));
        assertThatThrownBy(() -> service.refresh(refresh, metadata)).isInstanceOf(AuthException.class);
    }

    @Test void rejectsRevokedExpiredAndUnknownSessions() {
        activeAccount();
        String token = jwt.createRefreshToken(42).value();
        UserSession revoked = new UserSession(42, jwt.hashRefreshToken(token), metadata, clock.millis(), clock.millis() + 100000);
        revoked.revoke(clock.millis());
        UserSession expired = new UserSession(42, revoked.getRefreshTokenHash(), metadata, clock.millis() - 10000, clock.millis());
        when(sessions.lockSessionByHash(revoked.getRefreshTokenHash()))
                .thenReturn(Optional.of(revoked)).thenReturn(Optional.of(expired)).thenReturn(Optional.empty());
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.refresh(token, metadata)).isInstanceOf(AuthException.class);
        }
        verify(sessions, never()).saveSession(any());
    }

    @Test void rejectsRefreshForInactiveAccount() {
        activeAccount();
        ReflectionTestUtils.setField(account, "status", "DISABLED");
        assertThatThrownBy(() -> service.refresh(jwt.createRefreshToken(42).value(), metadata))
                .isInstanceOf(AuthException.class);
        verifyNoInteractions(sessions);
    }

    @Test void logoutRevokesSessionIdempotently() {
        activeAccount();
        String token = jwt.createRefreshToken(42).value();
        UserSession session = new UserSession(42, jwt.hashRefreshToken(token), metadata, clock.millis(), clock.millis() + 100000);
        when(sessions.lockSessionByHash(session.getRefreshTokenHash())).thenReturn(Optional.of(session));
        service.logout(42, token);
        service.logout(42, token);
        assertThat(session.getRevokedAt()).isEqualTo(clock.millis());
    }

    @Test void cannotLogoutAnotherUsersSession() {
        assertThatThrownBy(() -> service.logout(99, jwt.createRefreshToken(42).value())).isInstanceOf(AuthException.class);
        verifyNoInteractions(sessions, accounts);
    }

    @Test void logoutAllUsesAuthenticatedUser() {
        activeAccount();
        service.logoutAll(42);
        verify(accounts).lockAccount(42);
        verify(sessions).revokeAll(42, clock.millis());
    }

    @Test void meReturnsOnlyBasicProfile() {
        when(accounts.findAccountByUserId(42)).thenReturn(Optional.of(account));
        when(users.findUserById(42)).thenReturn(Optional.of(user));
        assertThat(service.me(42).username()).isEqualTo("test-user");
    }
}

