package fptu.exe202.signify.signifybe.storage.infrastructure.cloudflare;

import fptu.exe202.signify.signifybe.storage.infrastructure.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "storage",
        name = "provider",
        havingValue = "r2"
)
public class R2StorageConfiguration {

    private final StorageProperties properties;
    private final URI endpoint;
    private final Region region;
    private final StaticCredentialsProvider credentials;
    private final S3Configuration serviceConfiguration;

    public R2StorageConfiguration(
            StorageProperties properties,
            Environment environment
    ) {
        this.properties = properties;

        StorageProperties.requireText(
                properties.r2().bucket(),
                "storage.r2.bucket"
        );

        this.endpoint = StorageProperties.requireHttpsUrl(
                properties.r2().endpoint(),
                "storage.r2.endpoint"
        );

        if (!endpoint.getPath().isEmpty()
                && !endpoint.getPath().equals("/")) {
            throw new IllegalArgumentException(
                    "storage.r2.endpoint must not contain a path"
            );
        }

        this.region = Region.of(
                StorageProperties.requireText(
                        properties.r2().region(),
                        "storage.r2.region"
                )
        );

        this.credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                        StorageProperties.requireText(
                                environment.getProperty("R2_ACCESS_KEY_ID"),
                                "R2_ACCESS_KEY_ID"
                        ),
                        StorageProperties.requireText(
                                environment.getProperty("R2_SECRET_ACCESS_KEY"),
                                "R2_SECRET_ACCESS_KEY"
                        )
                )
        );

        this.serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false)
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Client r2Client() {
        return S3Client.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(serviceConfiguration)
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner r2Presigner() {
        return S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(serviceConfiguration)
                .build();
    }

    @Bean
    public R2ObjectStorage r2ObjectStorage(
            S3Client client,
            S3Presigner presigner
    ) {
        return new R2ObjectStorage(
                client,
                presigner,
                properties
        );
    }
}