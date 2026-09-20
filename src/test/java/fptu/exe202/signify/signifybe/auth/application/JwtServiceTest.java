package fptu.exe202.signify.signifybe.auth.application;

import fptu.exe202.signify.signifybe.auth.domain.Role;
import fptu.exe202.signify.signifybe.auth.domain.exception.AuthException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {
    private static final String ENCRYPTION_KEY = java.util.Base64.getEncoder()
            .encodeToString(io.jsonwebtoken.Jwts.ENC.A256GCM.key().build().getEncoded());
    private final String secret = UUID.randomUUID() + "-" + UUID.randomUUID();
    private final Instant now = Instant.parse("2026-09-20T00:00:00Z");
    private JwtService service;

    @BeforeEach void setUp() {
        service = serviceAt(now);
    }

    private JwtService serviceAt(Instant instant) {
        return new JwtService(new JwtProperties(secret, ENCRYPTION_KEY, 900000, 604800000), Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test void generatesAndValidatesAccessTokenWithRole() {
        var token = service.createAccessToken(42, Role.ADMIN);
        assertThat(service.validateAccessToken(token.value()).userId()).isEqualTo(42);
        assertThat(service.validateAccessToken(token.value()).role()).isEqualTo(Role.ADMIN);
        assertThat(token.expiresAt()).isEqualTo(now.plusSeconds(900).toEpochMilli());
        assertThat(token.toString()).doesNotContain(token.value());
    }

    @Test void generatesUniqueRefreshTokensForTheSameUserAndSecond() {
        var first = service.createRefreshToken(42);
        var second = service.createRefreshToken(42);
        assertThat(first.value()).isNotEqualTo(second.value());
        assertThat(service.validateRefreshToken(first.value())).isEqualTo(42);
        assertThat(first.expiresAt()).isEqualTo(now.plus(Duration.ofDays(7)).toEpochMilli());
    }

    @Test void rejectsExpiredAccessTokenAtExpirationBoundary() {
        var token = service.createAccessToken(42, Role.USER);
        assertThatThrownBy(() -> serviceAt(now.plusSeconds(900)).validateAccessToken(token.value()))
                .isInstanceOf(AuthException.class).hasMessage("Access token is invalid or expired");
    }

    @Test void rejectsExpiredRefreshToken() {
        var token = service.createRefreshToken(42);
        assertThatThrownBy(() -> serviceAt(now.plus(Duration.ofDays(7))).validateRefreshToken(token.value()))
                .isInstanceOf(AuthException.class);
    }

    @Test void rejectsTamperedAndMalformedTokens() {
        String token = service.createAccessToken(42, Role.USER).value();
        assertThatThrownBy(() -> service.validateAccessToken(token.substring(0, token.lastIndexOf('.') + 1) + "invalid"))
                .isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.validateAccessToken("not-a-jwt")).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.validateAccessToken(null)).isInstanceOf(AuthException.class);
    }

    @Test void rejectsDifferentSignature() {
        var other = new JwtService(new JwtProperties(UUID.randomUUID() + "-" + UUID.randomUUID(), ENCRYPTION_KEY, 900000, 604800000),
                Clock.fixed(now, ZoneOffset.UTC));
        assertThatThrownBy(() -> service.validateAccessToken(other.createAccessToken(42, Role.ADMIN).value()))
                .isInstanceOf(AuthException.class);
    }

    @Test void refreshAndAccessTokensAreNotInterchangeable() {
        assertThatThrownBy(() -> service.validateAccessToken(service.createRefreshToken(42).value()))
                .isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.validateRefreshToken(service.createAccessToken(42, Role.USER).value()))
                .isInstanceOf(AuthException.class);
    }

    @Test void rejectsMissingExpiryUnknownRoleAndWrongIssuer() {
        var key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        String noExpiry = Jwts.builder().issuer("signify-be").subject("42").claim("type", "access")
                .claim("role", "USER").issuedAt(Date.from(now)).signWith(key, Jwts.SIG.HS256).compact();
        String badRole = Jwts.builder().issuer("signify-be").subject("42").claim("type", "access")
                .claim("role", "ROOT").issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(900)))
                .signWith(key, Jwts.SIG.HS256).compact();
        String wrongIssuer = Jwts.builder().issuer("other-app").subject("42").claim("type", "access")
                .claim("role", "USER").issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(900)))
                .signWith(key, Jwts.SIG.HS256).compact();
        for (String token : new String[]{noExpiry, badRole, wrongIssuer}) {
            assertThatThrownBy(() -> service.validateAccessToken(token)).isInstanceOf(AuthException.class);
        }
    }

    @Test void hashesRefreshTokenDeterministicallyWithoutKeepingPlaintext() {
        var token = service.createRefreshToken(42).value();
        String hash = service.hashRefreshToken(token);
        assertThat(hash).matches("[0-9a-f]{64}").isNotEqualTo(token);
        assertThat(service.hashRefreshToken(token)).isEqualTo(hash);
    }

    @Test void refreshTokenEncryptsClaimsWithAuthenticatedAes256Gcm() {
        String token = service.createRefreshToken(42).value();
        String[] parts = token.split("\\.", -1);
        assertThat(parts).hasSize(5);
        assertThat(parts[1]).isEmpty(); // Direct encryption has no wrapped key.
        String header = new String(java.util.Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        assertThat(header).contains("\"alg\":\"dir\"", "\"enc\":\"A256GCM\"").doesNotContain("sub", "jti");
        String ciphertext = new String(java.util.Base64.getUrlDecoder().decode(parts[3]), StandardCharsets.UTF_8);
        assertThat(ciphertext).doesNotContain("\"sub\"", "\"type\"", "\"refresh\"");
        assertThat(service.validateRefreshToken(token)).isEqualTo(42);
    }

    @Test void rejectsTamperedCiphertextAndAuthenticationTag() {
        String token = service.createRefreshToken(42).value();
        for (int part : new int[]{3, 4}) {
            String[] parts = token.split("\\.", -1);
            byte[] bytes = java.util.Base64.getUrlDecoder().decode(parts[part]);
            bytes[0] ^= 1;
            parts[part] = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            assertThatThrownBy(() -> service.validateRefreshToken(String.join(".", parts)))
                    .isInstanceOf(AuthException.class).hasMessage("Refresh token is invalid or expired");
        }
    }

    @Test void rejectsRefreshTokenEncryptedWithAnotherKey() {
        String otherKey = java.util.Base64.getEncoder().encodeToString(Jwts.ENC.A256GCM.key().build().getEncoded());
        var other = new JwtService(new JwtProperties(secret, otherKey, 900000, 604800000), Clock.fixed(now, ZoneOffset.UTC));
        assertThatThrownBy(() -> service.validateRefreshToken(other.createRefreshToken(42).value()))
                .isInstanceOf(AuthException.class);
    }

    @Test void rejectsLegacySignedButUnencryptedRefreshToken() {
        String legacy = Jwts.builder().issuer("signify-be").subject("42").claim("type", "refresh")
                .id(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]))
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(900)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
        assertThatThrownBy(() -> service.validateRefreshToken(legacy)).isInstanceOf(AuthException.class);
    }

    @Test void rejectsEncryptedTokensWithWrongTypeAlgorithmOrMissingClaims() {
        var aes = new javax.crypto.spec.SecretKeySpec(java.util.Base64.getDecoder().decode(ENCRYPTION_KEY), "AES");
        String wrongType = Jwts.builder().issuer("signify-be").subject("42").claim("type", "access")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(900)))
                .encryptWith(aes, Jwts.ENC.A256GCM).compact();
        String missingExpiry = Jwts.builder().issuer("signify-be").subject("42").claim("type", "refresh")
                .issuedAt(Date.from(now)).encryptWith(aes, Jwts.ENC.A256GCM).compact();
        String otherAlgorithm = Jwts.builder().issuer("signify-be").subject("42").claim("type", "refresh")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(900)))
                .encryptWith(aes, Jwts.ENC.A128CBC_HS256).compact();
        for (String token : new String[]{wrongType, missingExpiry, otherAlgorithm}) {
            assertThatThrownBy(() -> service.validateRefreshToken(token)).isInstanceOf(AuthException.class);
        }
    }

    @Test void rejectsMissingMalformedAndWrongLengthEncryptionKeysWithoutExposingValues() {
        for (String invalid : new String[]{null, "", "not-base64!", java.util.Base64.getEncoder().encodeToString(new byte[16])}) {
            assertThatThrownBy(() -> new JwtService(new JwtProperties(secret, invalid, 900000, 604800000), Clock.systemUTC()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("JWT_REFRESH_ENCRYPTION_KEY must be Base64 encoding of exactly 32 random bytes")
                    .hasNoCause();
        }
        assertThat(new JwtProperties(secret, ENCRYPTION_KEY, 900000, 604800000).toString())
                .doesNotContain(secret, ENCRYPTION_KEY);
    }
    @Test void rejectsWeakConfigurationWithoutExposingIt() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("short", ENCRYPTION_KEY, 900000, 604800000), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("short");
        assertThat(new JwtProperties(secret, ENCRYPTION_KEY, 900000, 604800000).toString()).doesNotContain(secret);
    }
}
