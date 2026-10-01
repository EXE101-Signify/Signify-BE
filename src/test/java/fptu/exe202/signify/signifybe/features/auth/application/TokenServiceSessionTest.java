package fptu.exe202.signify.signifybe.features.auth.application;

import fptu.exe202.signify.signifybe.features.auth.application.port.out.UserSessionRepository;
import fptu.exe202.signify.signifybe.features.auth.domain.Account;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.auth.domain.SessionMetadata;
import fptu.exe202.signify.signifybe.features.auth.domain.UserSession;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TokenServiceSessionTest {
    @Test
    void persistsRefreshSessionBeforeIssuingSessionBoundAccessToken() {
        UserSessionRepository sessions = mock(UserSessionRepository.class);
        JwtService jwtService = mock(JwtService.class);
        Clock clock = Clock.systemUTC();
        TokenService tokenService = new TokenService(sessions, jwtService, clock);
        Account account = new Account(42L, "alice", "hash", clock.millis());
        UserSession persisted = mock(UserSession.class);

        when(jwtService.createRefreshToken(42L)).thenReturn(new JwtService.IssuedToken("opaque-refresh", 2000L));
        when(jwtService.hashRefreshToken("opaque-refresh")).thenReturn("sha256-hash");
        when(sessions.saveSession(any(UserSession.class))).thenReturn(persisted);
        when(persisted.getId()).thenReturn(321L);
        when(jwtService.createAccessToken(42L, 321L, "alice", Role.USER))
                .thenReturn(new JwtService.IssuedToken("signed-access", 1000L));

        TokenPair pair = tokenService.issueTokens(account, new SessionMetadata("device", "127.0.0.1", "test"));

        assertEquals("signed-access", pair.accessToken());
        assertEquals("opaque-refresh", pair.refreshToken());
        InOrder order = inOrder(jwtService, sessions, persisted);
        order.verify(jwtService).createRefreshToken(42L);
        order.verify(jwtService).hashRefreshToken("opaque-refresh");
        order.verify(sessions).saveSession(any(UserSession.class));
        order.verify(persisted).getId();
        order.verify(jwtService).createAccessToken(42L, 321L, "alice", Role.USER);
    }
}
