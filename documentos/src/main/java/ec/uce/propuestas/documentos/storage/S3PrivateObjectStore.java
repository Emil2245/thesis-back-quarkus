package ec.uce.propuestas.documentos.storage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** S3 keys and upload results are internal; no S3 URL or credential crosses this boundary. */
@ApplicationScoped
public class S3PrivateObjectStore {
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private final S3Client client;
    private final String bucket;

    @Inject
    public S3PrivateObjectStore(
            S3Client client, @ConfigProperty(name = "documentos.storage.bucket") Optional<String> bucket) {
        this(client, bucket.orElse(""));
    }

    public S3PrivateObjectStore(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    public StoredObject store(String key, byte[] bytes, String mimeType, String filename, String fingerprint) {
        validate(key, bytes, mimeType, filename, fingerprint);
        String checksum = sha256(bytes);
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(mimeType)
                            .metadata(Map.of(
                                    "fingerprint", fingerprint,
                                    "filename", filename,
                                    "expected-checksum-sha256", checksum,
                                    "expected-size", Integer.toString(bytes.length)))
                            .checksumSHA256(checksum)
                            .ifNoneMatch("*")
                            .build(),
                    RequestBody.fromBytes(bytes));
        } catch (S3Exception conflict) {
            if (conflict.statusCode() == 412) {
                throw new IllegalStateException("Object already exists; reconcile it before retrying", conflict);
            }
            throw conflict;
        }
        return confirm(key, bytes.length, checksum, mimeType, filename, fingerprint);
    }

    /** Known Put responses must match the exact bytes we just uploaded. */
    public StoredObject confirm(String key, byte[] bytes, String mimeType, String filename, String fingerprint) {
        validate(key, bytes, mimeType, filename, fingerprint);
        return confirm(key, bytes.length, sha256(bytes), mimeType, filename, fingerprint);
    }

    /** Recover an uncertain Put by its Core-bound fingerprint and S3-validated stored checksum.
     * Renderers may embed timestamps, so re-rendered bytes are not a valid expected checksum.
     */
    public StoredObject confirmExisting(String key, String mimeType, String filename, String fingerprint) {
        if (bucket.isBlank()
                || !validKey(key)
                || mimeType == null
                || mimeType.isBlank()
                || filename == null
                || !filename.matches("[A-Za-z0-9._-]{1,128}")
                || fingerprint == null
                || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid private object confirmation");
        }
        HeadObjectResponse head = client.headObject(HeadObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .checksumMode(ChecksumMode.ENABLED)
                .build());
        Long size = head.contentLength();
        String checksum = head.checksumSHA256();
        if (size == null
                || size <= 0
                || size > MAX_BYTES
                || checksum == null
                || !checksum.matches("[A-Za-z0-9+/]{43}=")
                || !checksum.equals(head.metadata().get("expected-checksum-sha256"))
                || !Long.toString(size).equals(head.metadata().get("expected-size"))
                || !fingerprint.equals(head.metadata().get("fingerprint"))
                || !mimeType.equals(head.contentType())
                || !filename.equals(head.metadata().get("filename"))) {
            throw new IllegalStateException("Cannot confirm private object with matching checksum and fingerprint");
        }
        return new StoredObject(key, size, checksum, mimeType, filename);
    }

    private StoredObject confirm(
            String key,
            long expectedSize,
            String expectedChecksum,
            String mimeType,
            String filename,
            String fingerprint) {
        StoredObject stored = confirmExisting(key, mimeType, filename, fingerprint);
        if (stored.size() != expectedSize || !stored.checksumSha256().equals(expectedChecksum)) {
            throw new IllegalStateException("Cannot confirm private object with matching checksum and fingerprint");
        }
        return stored;
    }

    private void validate(String key, byte[] bytes, String mimeType, String filename, String fingerprint) {
        if (bucket.isBlank()
                || !validKey(key)
                || bytes == null
                || bytes.length == 0
                || bytes.length > MAX_BYTES
                || mimeType == null
                || mimeType.isBlank()
                || filename == null
                || !filename.matches("[A-Za-z0-9._-]{1,128}")
                || fingerprint == null
                || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid private object upload");
        }
    }

    private static boolean validKey(String key) {
        if (key == null || !key.startsWith("exports/")) {
            return false;
        }
        try {
            String id = key.substring("exports/".length());
            UUID uuid = UUID.fromString(id);
            return uuid.version() == 7 && uuid.toString().equals(id);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return Base64.getEncoder()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    public record StoredObject(String key, long size, String checksumSha256, String mimeType, String filename) {}
}
