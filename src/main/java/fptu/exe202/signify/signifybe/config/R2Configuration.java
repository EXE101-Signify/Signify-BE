package fptu.exe202.signify.signifybe.config;


import lombok.experimental.FieldDefaults;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class R2Configuration {
    @Value("${cloudflare.r2.access-key-id}")
    String accessKey;

    @Value("${cloudflare.r2.secret-key-id}")
    String secretKey;

    @Value("${cloudflare.r2.end-point}")
    String endpoint;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        )
                )
                .region(Region.US_EAST_1)

                .serviceConfiguration(
                        software.amazon.awssdk.services.s3.S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .checksumValidationEnabled(false)
                                .chunkedEncodingEnabled(false)
                                .build()
                )

                .build();
    }
}