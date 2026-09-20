package fptu.exe202.signify.signifybe.storage.infrastructure.cloudflare;

import fptu.exe202.signify.signifybe.storage.infrastructure.S3CompatibleObjectStorage;
import fptu.exe202.signify.signifybe.storage.infrastructure.StorageProperties;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

public final class R2ObjectStorage extends S3CompatibleObjectStorage {
    public R2ObjectStorage(S3Client client, S3Presigner presigner, StorageProperties properties) {
        super(client, presigner, properties.r2().bucket(), properties);
    }
}
