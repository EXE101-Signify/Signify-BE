package fptu.exe202.signify.signifybe.features.auth.application;

import fptu.exe202.signify.signifybe.features.auth.application.port.out.AccountRepository;
import fptu.exe202.signify.signifybe.features.auth.application.port.out.UserSessionRepository;
import fptu.exe202.signify.signifybe.features.auth.domain.Account;
import fptu.exe202.signify.signifybe.features.auth.domain.SessionMetadata;
import fptu.exe202.signify.signifybe.features.auth.domain.UserSession;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import fptu.exe202.signify.signifybe.features.user.application.port.out.UserRepository;
import fptu.exe202.signify.signifybe.features.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceBanLifecycleTest {
    private static final long NOW = 2_000L;
    private static final SessionMetadata METADATA = new SessionMetadata("test", "127.0.0.1", "JUnit");

    @Mock AccountRepository accounts;
    @Mock UserRepository users;
    @Mock UserSessionRepository sessions;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock TokenService tokenService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");
        Clock clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);
        service = new AuthService(accounts, users, sessions, passwordEncoder, jwtService, clock, tokenService);
    }

    @Test
    void bannedUserCannotLoginButCanLoginAfterRestore() {
        User user = user();
        Account account = account();
        user.ban(1_500L);
        account.ban(1_500L);
        when(accounts.lockAccountByUsername("user2")).thenReturn(Optional.of(account));
        when(users.findUserById(2L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "password-hash")).thenReturn(true);

        assertThrows(AuthException.class, () -> service.login("user2", "password", METADATA));

        user.restore(NOW);
        account.activate(NOW);
        TokenPair tokens = new TokenPair("access", "refresh", 3_000L, 4_000L);
        when(tokenService.issueTokens(account, METADATA)).thenReturn(tokens);

        AuthResult result = service.login("user2", "password", METADATA);

        assertEquals(2L, result.user().userId());
        assertSame(tokens, result.tokens());
    }

    @Test
    void revokedRefreshSessionStaysInvalidAfterRestore() {
        User user = user();
        Account account = account();
        UserSession oldSession = new UserSession(2L, "old-hash", METADATA, 1_000L, 10_000L);
        oldSession.revoke(1_500L);
        when(jwtService.validateRefreshToken("old-refresh")).thenReturn(2L);
        when(jwtService.hashRefreshToken("old-refresh")).thenReturn("old-hash");
        when(accounts.lockAccount(2L)).thenReturn(Optional.of(account));
        when(users.findUserById(2L)).thenReturn(Optional.of(user));
        when(sessions.lockSessionByHash("old-hash")).thenReturn(Optional.of(oldSession));

        assertThrows(AuthException.class, () -> service.refresh("old-refresh", METADATA));

        verify(tokenService, never()).issueTokens(any(), any());
    }

    private User user() {
        User user = new User("user@example.com", "Test", "User", 1_000L);
        user.setId(2L);
        return user;
    }

    private Account account() {
        return new Account(2L, "user2", "password-hash", 1_000L);
    }
}
