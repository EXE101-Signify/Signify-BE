package fptu.exe202.signify.signifybe.auth.application;

import fptu.exe202.signify.signifybe.features.auth.application.*;
import fptu.exe202.signify.signifybe.features.auth.domain.*;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import fptu.exe202.signify.signifybe.features.auth.application.port.out.UserSessionRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtPayloadTest {
    private static final String SECRET = "test-only-signing-secret-with-at-least-32-bytes";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-22T10:00:00.123Z"), ZoneOffset.UTC);
    private final JwtProperties properties = new JwtProperties(SECRET,
            Base64.getEncoder().encodeToString(new byte[32]), 900000, 604800000,
            "test-issuer", "test-client");
    private final JwtService service = new JwtService(properties, CLOCK);

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .clock(() -> Date.from(CLOCK.instant())).build().parseSignedClaims(token).getPayload();
    }

    @Test void issuesRequiredClaimsWithoutSensitiveDataAndWithUniqueIds() {
        var first = service.createAccessToken(42, "huybg", Role.ADMIN);
        Claims payload = claims(first.value());
        assertThat(payload.keySet()).containsExactlyInAnyOrder(
                "sub", "username", "roles", "iat", "exp", "iss", "aud", "jti", "role", "type");
        assertThat(payload.getSubject()).isEqualTo("42");
        assertThat(payload.get("username")).isEqualTo("huybg");
        assertThat(payload.get("roles")).isEqualTo(List.of("ADMIN"));
        assertThat(payload.get("role")).isEqualTo("ADMIN");
        assertThat(payload.getIssuer()).isEqualTo(properties.issuer());
        assertThat(payload.getAudience()).containsExactly(properties.audience());
        assertThat(payload.getIssuedAt().getTime()).isEqualTo(CLOCK.millis() / 1000 * 1000);
        assertThat(payload.getExpiration().getTime()).isEqualTo(first.expiresAt())
                .isEqualTo((CLOCK.millis() + properties.expiration()) / 1000 * 1000);
        assertThat(UUID.fromString(payload.getId()).toString()).isEqualTo(payload.getId());
        assertThat(claims(service.createAccessToken(42, "huybg", Role.ADMIN).value()).getId())
                .isNotEqualTo(payload.getId());
        assertThat(service.validateAccessToken(first.value())).isEqualTo(new CurrentUser(42, Role.ADMIN));
    }

    private JwtBuilder builder() {
        return Jwts.builder().subject("42").issuer(properties.issuer()).claim("role", "USER")
                .claim("type", "access").issuedAt(Date.from(CLOCK.instant()))
                .expiration(Date.from(CLOCK.instant().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256);
    }

    private void rejects(String token) {
        assertThatThrownBy(() -> service.validateAccessToken(token)).isInstanceOf(AuthException.class);
    }

    @Test void rejectsInvalidSignatureIssuerAudienceExpiryAndType() {
        rejects(builder().signWith(Keys.hmacShaKeyFor("different-test-key-with-at-least-32-bytes".getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact());
        rejects(builder().issuer("other-issuer").compact());
        rejects(builder().audience().add("other-client").and().compact());
        rejects(builder().expiration(Date.from(CLOCK.instant().minusSeconds(1))).compact());
        rejects(builder().expiration(null).compact());
        rejects(builder().issuer(null).compact());
        rejects(builder().claim("type", "refresh").compact());
        rejects(builder().issuedAt(Date.from(CLOCK.instant().plusSeconds(30))).compact());
        rejects(builder().subject("huybg").compact());
        rejects("malformed-token");
    }

    @Test void preservesLegacyAccessTokensWithoutAudience() {
        assertThat(service.validateAccessToken(builder().compact())).isEqualTo(new CurrentUser(42, Role.USER));
    }

    @Test void expiredGeneratedAccessTokenIsRejected() {
        var token = service.createAccessToken(42, "huybg", Role.USER);
        var later = new JwtService(properties, Clock.fixed(Instant.ofEpochMilli(token.expiresAt()), ZoneOffset.UTC));
        assertThatThrownBy(() -> later.validateAccessToken(token.value())).isInstanceOf(AuthException.class);
    }

    @Test void refreshEncryptionAndTokenSeparationRemainUnchanged() {
        var refresh = service.createRefreshToken(42);
        assertThat(service.validateRefreshToken(refresh.value())).isEqualTo(42);
        assertThat(refresh.value().split("\\.", -1)).hasSize(5);
        rejects(refresh.value());
        assertThatThrownBy(() -> service.validateRefreshToken(service.createAccessToken(42, "huybg", Role.USER).value()))
                .isInstanceOf(AuthException.class);
    }

    @Test void tokenServiceUsesPersistedAccountIdentityAndRole() {
        Account account = mock(Account.class);
        when(account.getUserId()).thenReturn(42L);
        when(account.getUsername()).thenReturn("database-username");
        when(account.getRole()).thenReturn(Role.ADMIN);
        UserSessionRepository sessions = mock(UserSessionRepository.class);
        TokenPair pair = new TokenService(sessions, service, CLOCK).issueTokens(account,
                new SessionMetadata(null, null, null));
        assertThat(claims(pair.accessToken()).get("username")).isEqualTo("database-username");
        assertThat(claims(pair.accessToken()).get("roles")).isEqualTo(List.of("ADMIN"));
        assertThat(service.validateRefreshToken(pair.refreshToken())).isEqualTo(42);
        verify(sessions).saveSession(any(UserSession.class));
    }

    @Test void rejectsBlankConfiguration() {
        assertThatThrownBy(() -> new JwtProperties(SECRET, properties.refreshEncryptionKey(), 900000, 604800000, "", "client"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties(SECRET, properties.refreshEncryptionKey(), 900000, 604800000, "issuer", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
