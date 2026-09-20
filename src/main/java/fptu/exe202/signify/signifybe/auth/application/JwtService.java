package fptu.exe202.signify.signifybe.auth.application;

import fptu.exe202.signify.signifybe.auth.domain.CurrentUser;
import fptu.exe202.signify.signifybe.auth.domain.Role;
import fptu.exe202.signify.signifybe.auth.domain.exception.AuthException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
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

@Service
public class JwtService {
    private static final String ISSUER = "signify-be";
    private final SecretKey key;
    private final SecretKey refreshEncryptionKey;
    private final JwtParser refreshParser;
    private final JwtProperties properties;
    private final Clock clock;
    private final JwtParser parser;
    private final SecureRandom random = new SecureRandom();

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        String secret = properties.secret();
        if (secret == null || secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes of random secret material");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.refreshEncryptionKey = readEncryptionKey(properties.refreshEncryptionKey());
        this.refreshParser = Jwts.parser().decryptWith(refreshEncryptionKey).requireIssuer(ISSUER)
                .clock(() -> Date.from(clock.instant())).build();
        this.parser = Jwts.parser().verifyWith(key).requireIssuer(ISSUER)
                .clock(() -> Date.from(clock.instant())).build();
    }

    public IssuedToken createAccessToken(long userId, Role role) {
        long now = clock.millis();
        long expiresAt = Math.addExact(now, properties.expiration()) / 1000 * 1000;
        String token = builder(userId, "access", now, expiresAt)
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
            return new CurrentUser(userId(claims), Role.valueOf(claims.get("role", String.class)));
        } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
            throw AuthException.invalidAccessToken();
        }
    }

    public long validateRefreshToken(String token) {
        try {
            validateTokenLength(token);
            Jwe<Claims> encrypted = refreshParser.parseEncryptedClaims(token);
            if (!"dir".equals(encrypted.getHeader().getAlgorithm())
                    || !"A256GCM".equals(encrypted.getHeader().getEncryptionAlgorithm())) {
                throw new IllegalArgumentException();
            }
            Claims claims = validateClaims(encrypted.getPayload(), "refresh");
            if (claims.getId() == null || !claims.getId().matches("[A-Za-z0-9_-]{43}")) {
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
        return Jwts.builder().issuer(ISSUER).subject(Long.toString(userId)).claim("type", type)
                .issuedAt(new Date(now)).expiration(new Date(expiresAt));
    }

    private Claims parse(String token, String type) {
        validateTokenLength(token);
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

    private void validateTokenLength(String token) {
        if (token == null || token.isBlank() || token.length() > 4096) throw new IllegalArgumentException();
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

    public record IssuedToken(String value, long expiresAt) {
        @Override public String toString() { return "IssuedToken[value=REDACTED]"; }
    }
}
