package fptu.exe202.signify.signifybe.storage.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("storage")
public record StorageProperties(
        @DefaultValue("s3") Provider provider,
        String publicBaseUrl,
        @DefaultValue("15m") Duration presignedUrlDuration,
        @DefaultValue S3 s3,
        @DefaultValue R2 r2) {

    public enum Provider { s3, r2 }

    public record S3(String bucket, String region) { }

    public record R2(String bucket, String endpoint, @DefaultValue("auto") String region) { }

    public StorageProperties {
        if (presignedUrlDuration == null || presignedUrlDuration.compareTo(Duration.ofSeconds(1)) < 0
                || presignedUrlDuration.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("storage.presigned-url-duration must be between 1s and 7d");
        }
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            requireHttpsUrl(publicBaseUrl, "storage.public-base-url");
            publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        }
    }

    public static String requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(property + " must be configured");
        }
        return value;
    }

    public static URI requireHttpsUrl(String value, String property) {
        requireText(value, property);
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(property + " must be an HTTPS URL without credentials, query or fragment");
        }
    }
}
