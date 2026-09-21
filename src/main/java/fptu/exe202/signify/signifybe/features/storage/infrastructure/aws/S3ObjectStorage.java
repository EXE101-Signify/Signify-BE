package fptu.exe202.signify.signifybe.features.storage.infrastructure.aws;

import fptu.exe202.signify.signifybe.features.storage.infrastructure.S3CompatibleObjectStorage;
import fptu.exe202.signify.signifybe.features.storage.infrastructure.StorageProperties;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

public final class S3ObjectStorage extends S3CompatibleObjectStorage {
    public S3ObjectStorage(S3Client client, S3Presigner presigner, StorageProperties properties) {
        super(client, presigner, properties.s3().bucket(), properties);
    }
}
