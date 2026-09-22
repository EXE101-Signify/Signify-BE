package fptu.exe202.signify.signifybe.features.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, String refreshEncryptionKey, @DefaultValue("900000") long expiration,
                            @DefaultValue("604800000") long refreshExpiration,
                            String issuer, String audience) {
    public JwtProperties {
        if (expiration < 1000 || refreshExpiration < 1000) {
            throw new IllegalArgumentException("JWT expiration values must be at least 1000 milliseconds");
        }
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT issuer and audience must not be blank");
        }
    }

    @Override public String toString() { return "JwtProperties[secret=REDACTED, refreshEncryptionKey=REDACTED]"; }
}
