package fptu.exe202.signify.signifybe.features.storage.infrastructure.aws;

import fptu.exe202.signify.signifybe.features.storage.infrastructure.StorageProperties;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "storage", name = "provider", havingValue = "s3", matchIfMissing = true)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class S3StorageConfiguration {

    StorageProperties properties;

    Region region;

    public S3StorageConfiguration(StorageProperties properties) {
        this.properties = properties;
        StorageProperties.requireText(properties.s3().bucket(), "storage.s3.bucket");
        this.region = Region.of(StorageProperties.requireText(properties.s3().region(), "storage.s3.region"));
    }

    @Bean(destroyMethod = "close")
    public S3Client s3Client() {
        return S3Client.builder().region(region)
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create()).build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner() {
        return S3Presigner.builder().region(region)
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create()).build();
    }

    @Bean
    public S3ObjectStorage s3ObjectStorage(S3Client client, S3Presigner presigner) {
        return new S3ObjectStorage(client, presigner, properties);
    }
}
