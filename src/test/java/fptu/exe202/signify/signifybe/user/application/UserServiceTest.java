package fptu.exe202.signify.signifybe.user.application;

import fptu.exe202.signify.signifybe.features.auth.application.JwtProperties;
import fptu.exe202.signify.signifybe.features.auth.application.JwtService;
import fptu.exe202.signify.signifybe.features.auth.application.TokenService;
import fptu.exe202.signify.signifybe.features.auth.application.port.out.AccountRepository;
import fptu.exe202.signify.signifybe.features.auth.application.port.out.UserSessionRepository;
import fptu.exe202.signify.signifybe.features.auth.domain.Account;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.auth.domain.SessionMetadata;
import fptu.exe202.signify.signifybe.features.auth.domain.UserSession;
import fptu.exe202.signify.signifybe.features.user.application.UserService;
import fptu.exe202.signify.signifybe.features.user.domain.exception.UserException;

import fptu.exe202.signify.signifybe.features.user.application.port.out.UserRepository;
import fptu.exe202.signify.signifybe.features.user.domain.User;
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

class UserServiceTest {
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
    private UserService service;
    private User user;
    private Account account;

    @BeforeEach void setUp() {
        service = new UserService(accounts, users, encoder, new TokenService(sessions, jwt, clock), clock);
        user = new User(null, "Test", "User", clock.millis());
        ReflectionTestUtils.setField(user, "id", 42L);
        account = new Account(42, "test-user", encoder.encode("password123"), clock.millis());
    }

    @Test void registrationCreatesUserAccountAndHashedSession() {
        when(users.addUser(any())).thenReturn(user);
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
                .isInstanceOf(UserException.class).hasMessage("Username already exists");
        verifyNoInteractions(users, sessions);
    }

    @Test void rejectsPasswordsExceedingBcryptByteLimit() {
        assertThatThrownBy(() -> service.register("test-user", "\u00e9".repeat(37), null, null, null, metadata))
                .isInstanceOf(UserException.class);
        verifyNoInteractions(users, accounts, sessions);
    }

    @Test void meReturnsOnlyBasicProfile() {
        when(accounts.findAccountByUserId(42)).thenReturn(Optional.of(account));
        when(users.findUserById(42)).thenReturn(Optional.of(user));
        assertThat(service.me(42).username()).isEqualTo("test-user");
    }
}
