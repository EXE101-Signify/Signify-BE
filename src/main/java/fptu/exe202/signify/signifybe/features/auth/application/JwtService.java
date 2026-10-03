package fptu.exe202.signify.signifybe.features.auth.application;

import fptu.exe202.signify.signifybe.common.AuthValidation;
import fptu.exe202.signify.signifybe.features.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.features.auth.domain.Role;
import fptu.exe202.signify.signifybe.features.auth.domain.exception.AuthException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JwtService {

    SecretKey key;

    SecretKey refreshEncryptionKey;

    JwtParser refreshParser;

    JwtProperties properties;

    Clock clock;

    JwtParser parser;

    SecureRandom random = new SecureRandom();

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        String secret = properties.secret();
        if (secret == null || secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes of random secret material");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.refreshEncryptionKey = readEncryptionKey(properties.refreshEncryptionKey());
        this.refreshParser = Jwts.parser().decryptWith(refreshEncryptionKey).requireIssuer(properties.issuer())
                .clock(() -> Date.from(clock.instant())).build();
        this.parser = Jwts.parser().verifyWith(key).requireIssuer(properties.issuer())
                .clock(() -> Date.from(clock.instant())).build();
    }

    public IssuedToken createAccessToken(long userId, long sessionId, String username, Role role) {
        if (userId <= 0 || sessionId <= 0 || username == null || username.isBlank() || role == null) {
            throw new IllegalArgumentException("Access tokens require a persisted user, session, username and role");
        }
        long now = clock.millis();
        long expiresAt = Math.addExact(now, properties.expiration()) / 1000 * 1000;
        String token = builder(userId, "access", now, expiresAt)
                .claim("sid", sessionId)
                .claim("username", username).claim("roles", List.of(role.name()))
                .audience().add(properties.audience()).and().id(UUID.randomUUID().toString())
                .claim("role", role.name()).signWith(key, Jwts.SIG.HS256).compact();
        return new IssuedToken(token, expiresAt);
    }

    public IssuedToken createRefreshToken(long userId) {
        long now = clock.millis();
        long expiresAt = Math.addExact(now, properties.refreshExpiration()) / 1000 * 1000;
        byte[] nonce = new byte[32];
        random.nextBytes(nonce);
        String token = builder(userId, "refresh", now, expiresAt)
                .id(Base64.getUrlEncoder().withoutPadding().encodeToString(nonce))
                .encryptWith(refreshEncryptionKey, Jwts.ENC.A256GCM).compact();
        return new IssuedToken(token, expiresAt);
    }

    public CurrentUser validateAccessToken(String token) {
        try {
            Claims claims = parse(token, "access");
            if (claims.containsKey("aud") && !claims.getAudience().contains(properties.audience())) {
                throw new IllegalArgumentException();
            }
            return new CurrentUser(userId(claims), sessionId(claims),
                    Role.valueOf(claims.get("role", String.class)));
        } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
            throw AuthException.invalidAccessToken();
        }
    }

    public long validateRefreshToken(String token) {
        try {
            AuthValidation.validateTokenLength(token);
            Jwe<Claims> encrypted = refreshParser.parseEncryptedClaims(token);
            if (!"dir".equals(encrypted.getHeader().getAlgorithm())
                    || !"A256GCM".equals(encrypted.getHeader().getEncryptionAlgorithm())) {
                throw new IllegalArgumentException();
            }
            Claims claims = validateClaims(encrypted.getPayload(), "refresh");
            if (claims.getId() == null || !claims.getId().matches(AuthValidation.REFRESH_TOKEN_ID_REGEX)) {
                throw new IllegalArgumentException();
            }
            return userId(claims);
        } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
            throw AuthException.invalidRefreshToken();
        }
    }

    public String hashRefreshToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Required SHA-256 algorithm is unavailable");
        }
    }

    private JwtBuilder builder(long userId, String type, long now, long expiresAt) {
        return Jwts.builder().issuer(properties.issuer()).subject(Long.toString(userId)).claim("type", type)
                .issuedAt(new Date(now)).expiration(new Date(expiresAt));
    }

    private Claims parse(String token, String type) {
        AuthValidation.validateTokenLength(token);
        Jws<Claims> signed = parser.parseSignedClaims(token);
        if (!"HS256".equals(signed.getHeader().getAlgorithm())) throw new IllegalArgumentException();
        return validateClaims(signed.getPayload(), type);
    }

    private Claims validateClaims(Claims claims, String type) {
        if (!type.equals(claims.get("type", String.class)) || claims.getExpiration() == null
                || claims.getIssuedAt() == null || claims.getIssuedAt().getTime() > clock.millis()
                || claims.getExpiration().getTime() <= clock.millis()) {
            throw new IllegalArgumentException();
        }
        return claims;
    }

    private SecretKey readEncryptionKey(String encodedKey) {
        try {
            if (encodedKey == null || encodedKey.isBlank()) throw new IllegalArgumentException();
            byte[] bytes = Base64.getDecoder().decode(encodedKey);
            if (bytes.length != 32) throw new IllegalArgumentException();
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException ex) {
            // Do not attach the decoding exception: it may contain configuration details.
            throw new IllegalStateException("JWT_REFRESH_ENCRYPTION_KEY must be Base64 encoding of exactly 32 random bytes");
        }
    }
    private long userId(Claims claims) {
        long id = Long.parseLong(claims.getSubject());
        if (id <= 0) throw new IllegalArgumentException();
        return id;
    }

    private long sessionId(Claims claims) {
        Object value = claims.get("sid");
        if (!(value instanceof Number number)) throw new IllegalArgumentException();
        try {
            long id = new java.math.BigDecimal(number.toString()).longValueExact();
            if (id <= 0) throw new IllegalArgumentException();
            return id;
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new IllegalArgumentException();
        }
    }

    public record IssuedToken(String value, long expiresAt) {
        @Override public String toString() { return "IssuedToken[value=REDACTED]"; }
    }
}
