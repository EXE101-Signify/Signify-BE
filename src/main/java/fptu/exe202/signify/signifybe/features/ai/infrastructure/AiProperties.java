package fptu.exe202.signify.signifybe.features.ai.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("signify.ai")
public record AiProperties(String baseUrl,
                           @DefaultValue("2s") Duration connectTimeout,
                           @DefaultValue("5s") Duration readTimeout) {
    public AiProperties {
        try {
            URI uri = URI.create(baseUrl);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            baseUrl = baseUrl.replaceAll("/+$", "");
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("signify.ai.base-url must be an HTTP(S) URL", ex);
        }
        validateTimeout(connectTimeout, "connect-timeout");
        validateTimeout(readTimeout, "read-timeout");
    }

    private static void validateTimeout(Duration value, String name) {
        if (value == null || value.toMillis() < 1 || value.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("signify.ai." + name + " must be between 1ms and 2147483647ms");
        }
    }
}
