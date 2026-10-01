package fptu.exe202.signify.signifybe.features.security;

import fptu.exe202.signify.apiresponse.exception.BaseException;
import fptu.exe202.signify.signifybe.features.auth.application.AccountAccessService;
import fptu.exe202.signify.signifybe.features.auth.application.JwtProperties;
import fptu.exe202.signify.signifybe.features.auth.application.JwtService;
import fptu.exe202.signify.signifybe.features.auth.application.SessionAccessService;
import fptu.exe202.signify.signifybe.features.auth.application.port.out.UserSessionRepository;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.ModelAndView;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterSessionTest {
    private static final long USER_ID = 42L;
    private static final long SESSION_ONE = 321L;
    private static final long SESSION_TWO = 654L;
    private static final String SECRET = "session-aware-test-secret-with-at-least-32-bytes";
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);

    private UserSessionRepository sessions;
    private JwtService jwtService;
    private AccountAccessService accountAccess;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        sessions = mock(UserSessionRepository.class);
        jwtService = new JwtService(new JwtProperties(SECRET,
                "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=", 900_000, 604_800_000,
                "signify-test", "signify-test-client"), clock);
        accountAccess = mock(AccountAccessService.class);
        when(accountAccess.requireActiveUser(anyLong(), anyLong())).thenAnswer(invocation ->
                new CurrentUser(invocation.getArgument(0), invocation.getArgument(1), Role.USER));
        SessionAccessService sessionAccess = new SessionAccessService(sessions, clock);
        ApiSecurityErrorHandler errors = new ApiSecurityErrorHandler((request, response, handler, exception) -> {
            response.setStatus(((BaseException) exception).getStatus().value());
            return new ModelAndView();
        });
        filter = new JwtAuthenticationFilter(jwtService, errors, accountAccess, sessionAccess);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void oldAccessTokenGets401AfterItsSessionIsRevokedWhileOtherSessionStaysActive() throws Exception {
        when(sessions.isActiveSession(SESSION_ONE, USER_ID, clock.millis())).thenReturn(true, false);
        when(sessions.isActiveSession(SESSION_TWO, USER_ID, clock.millis())).thenReturn(true);
        String firstToken = jwtService.createAccessToken(USER_ID, SESSION_ONE, "alice", Role.USER).value();
        String secondToken = jwtService.createAccessToken(USER_ID, SESSION_TWO, "alice", Role.USER).value();

        var firstResponse = invoke(firstToken);
        assertEquals(200, firstResponse.response().getStatus());
        assertTrue(firstResponse.chainReached());

        // Simulate logout revoking only session one in the persisted session repository.
        var revokedResponse = invoke(firstToken);
        assertEquals(401, revokedResponse.response().getStatus());
        assertFalse(revokedResponse.chainReached());

        var otherSessionResponse = invoke(secondToken);
        assertEquals(200, otherSessionResponse.response().getStatus());
        assertTrue(otherSessionResponse.chainReached());
    }

    @Test
    void rejectsSessionThatDoesNotExistOrBelongsToAnotherUser() throws Exception {
        when(sessions.isActiveSession(SESSION_ONE, USER_ID, clock.millis())).thenReturn(false);
        String token = jwtService.createAccessToken(USER_ID, SESSION_ONE, "alice", Role.USER).value();

        var result = invoke(token);
        assertEquals(401, result.response().getStatus());
        assertFalse(result.chainReached());
        verify(accountAccess, never()).requireActiveUser(anyLong(), anyLong());
    }

    private InvocationResult invoke(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] reached = {false};
        filter.doFilter(request, response, (req, res) -> reached[0] = true);
        return new InvocationResult(response, reached[0]);
    }

    private record InvocationResult(MockHttpServletResponse response, boolean chainReached) { }
}
