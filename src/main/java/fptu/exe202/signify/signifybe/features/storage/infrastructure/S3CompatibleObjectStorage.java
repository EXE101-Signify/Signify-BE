package fptu.exe202.signify.signifybe.features.storage.infrastructure;

import fptu.exe202.signify.signifybe.features.storage.application.port.out.ObjectStorage;
import fptu.exe202.signify.signifybe.features.storage.domain.*;
import fptu.exe202.signify.signifybe.storage.domain.*;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;

/** Shared S3 protocol operations; provider configuration remains in the concrete adapters. */
public abstract class S3CompatibleObjectStorage implements ObjectStorage {
    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private final String publicBaseUrl;
    private final Duration urlDuration;

    protected S3CompatibleObjectStorage(S3Client client, S3Presigner presigner,
                                        String bucket, StorageProperties properties) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
        this.publicBaseUrl = properties.publicBaseUrl();
        this.urlDuration = properties.presignedUrlDuration();
    }

    @Override
    public StorageObject upload(String key, InputStream inputStream, String contentType, long contentLength) {
        StorageKey.validate(key);
        // Resolve signing before writing, so a signing failure cannot leave an orphan upload.
        String url = generateUrl(key);
        try {
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                            .contentType(contentType).contentLength(contentLength).build(),
                    RequestBody.fromInputStream(inputStream, contentLength));
            return new StorageObject(key, url, contentType, contentLength);
        } catch (SdkException ex) {
            throw new StorageUploadException();
        }
    }

    @Override
    public void delete(String key) {
        StorageKey.validate(key);
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException ex) {
            throw new StorageDeleteException();
        }
    }

    @Override
    public String generateUrl(String key) {
        StorageKey.validate(key);
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            // Keys consist solely of URL-safe characters validated above.
            return publicBaseUrl + "/" + key;
        }
        try {
            return presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(urlDuration)
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .build()).url().toExternalForm();
        } catch (SdkException ex) {
            throw StorageException.unavailable();
        }
    }

    @Override
    public boolean exists(String key) {
        StorageKey.validate(key);
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw StorageException.unavailable();
        } catch (SdkException ex) {
            throw StorageException.unavailable();
        }
    }
}
