package fptu.exe202.signify.signifybe.features.auth.application;

import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceSessionTest {
    private static final String SECRET = "session-aware-test-secret-with-at-least-32-bytes";
    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(SECRET,
                "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=", 900_000, 604_800_000,
                "signify-test", "signify-test-client"), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void accessTokenCarriesUserAndSessionIds() {
        var issued = jwtService.createAccessToken(42L, 321L, "alice", Role.USER);

        CurrentUser user = jwtService.validateAccessToken(issued.value());
        assertEquals(42L, user.userId());
        assertEquals(321L, user.sessionId());
        assertEquals(Role.USER, user.role());
    }

    @Test
    void rejectsNonPositiveUserOrSessionIdsWhenIssuing() {
        assertThrows(IllegalArgumentException.class,
                () -> jwtService.createAccessToken(0, 1, "alice", Role.USER));
        assertThrows(IllegalArgumentException.class,
                () -> jwtService.createAccessToken(1, 0, "alice", Role.USER));
    }

    @Test
    void rejectsTokensWithoutPositiveIntegerSessionClaim() {
        assertInvalid(accessTokenWithoutSid());
        assertInvalid(accessTokenWithSid("12"));
        assertInvalid(accessTokenWithSid(0));
        assertInvalid(accessTokenWithSid(1.5));
    }

    @Test
    void rejectsWrongTokenTypeEvenWhenSessionClaimIsValid() {
        String token = Jwts.builder().issuer("signify-test").subject("42")
                .claim("type", "refresh").claim("sid", 321).claim("role", "USER")
                .issuedAt(Date.from(NOW)).expiration(Date.from(NOW.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
        assertInvalid(token);
    }

    private String accessTokenWithoutSid() {
        return accessTokenBuilder().compact();
    }

    private String accessTokenWithSid(Object sid) {
        return accessTokenBuilder().claim("sid", sid).compact();
    }

    private io.jsonwebtoken.JwtBuilder accessTokenBuilder() {
        return Jwts.builder().issuer("signify-test").subject("42").claim("type", "access")
                .claim("username", "alice").claim("roles", List.of("USER")).claim("role", "USER")
                .audience().add("signify-test-client").and()
                .issuedAt(Date.from(NOW)).expiration(Date.from(NOW.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256);
    }

    private void assertInvalid(String token) {
        assertThrows(AuthException.class, () -> jwtService.validateAccessToken(token));
    }
}
