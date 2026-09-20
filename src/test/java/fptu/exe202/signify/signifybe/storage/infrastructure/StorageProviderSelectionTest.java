package fptu.exe202.signify.signifybe.storage.infrastructure;

import fptu.exe202.signify.signifybe.storage.application.port.out.ObjectStorage;
import fptu.exe202.signify.signifybe.storage.infrastructure.aws.*;
import fptu.exe202.signify.signifybe.storage.infrastructure.cloudflare.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;

class StorageProviderSelectionTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StorageConfiguration.class, S3StorageConfiguration.class, R2StorageConfiguration.class);

    @Test
    void defaultsToS3WithoutR2Configuration() {
        runner.withPropertyValues("storage.s3.bucket=test-bucket", "storage.s3.region=us-east-1")
                .run(context -> {
                    assertThat(context).hasSingleBean(ObjectStorage.class).hasSingleBean(S3Client.class)
                            .hasSingleBean(S3Presigner.class);
                    assertThat(context.getBean(ObjectStorage.class)).isInstanceOf(S3ObjectStorage.class);
                    assertThat(context).doesNotHaveBean(R2ObjectStorage.class);
                });
    }

    @Test
    void explicitlySelectsS3() {
        runner.withPropertyValues("storage.provider=s3", "storage.s3.bucket=test-bucket", "storage.s3.region=us-east-1")
                .run(context -> assertThat(context.getBean(ObjectStorage.class)).isInstanceOf(S3ObjectStorage.class));
    }

    @Test
    void selectsR2WithoutAwsConfiguration() {
        runner.withPropertyValues("storage.provider=r2", "storage.r2.bucket=test-bucket",
                        "storage.r2.endpoint=https://account.r2.cloudflarestorage.com",
                        "R2_ACCESS_KEY_ID=test-only-key", "R2_SECRET_ACCESS_KEY=test-only-secret")
                .run(context -> {
                    assertThat(context).hasSingleBean(ObjectStorage.class).hasSingleBean(S3Client.class)
                            .hasSingleBean(S3Presigner.class);
                    assertThat(context.getBean(ObjectStorage.class)).isInstanceOf(R2ObjectStorage.class);
                    assertThat(context).doesNotHaveBean(S3ObjectStorage.class);
                    assertThat(context.getBean(S3Client.class).serviceClientConfiguration().region().id()).isEqualTo("auto");
                });
    }

    @Test
    void rejectsUnknownProvider() {
        runner.withPropertyValues("storage.provider=local").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsMissingSelectedBucket() {
        runner.withPropertyValues("storage.provider=s3", "storage.s3.region=us-east-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsNonPositiveImageLimit() {
        runner.withPropertyValues("storage.s3.bucket=test", "storage.s3.region=us-east-1", "storage.image.max-size=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsExcessiveUrlLifetime() {
        runner.withPropertyValues("storage.presigned-url-duration=8d").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsUnsafePublicUrl() {
        runner.withPropertyValues("storage.public-base-url=https://user:password@example.com")
                .run(context -> assertThat(context).hasFailed());
    }
}
