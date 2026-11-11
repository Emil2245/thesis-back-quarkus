package ec.uce.propuestas.documento.exportacion;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/** Core-only bounded private S3 read, checked against the confirmed V023 object metadata. */
@ApplicationScoped
public class CorePrivateObjectReader {
    private static final int MAX_BYTES = 64 * 1024 * 1024;

    @Inject
    S3Client s3;

    @ConfigProperty(name = "core.storage.bucket")
    Optional<String> configuredBucket;

    public CorePrivateObjectReader() {}

    CorePrivateObjectReader(S3Client s3, String bucket) {
        this.s3 = Objects.requireNonNull(s3, "s3");
        this.configuredBucket = Optional.ofNullable(bucket);
    }

    public byte[] read(DownloadTicketService.ConfirmedObject object) {
        Objects.requireNonNull(object, "object");
        if (configuredBucket.isEmpty() || !configuredBucket.get().matches("[A-Za-z0-9][A-Za-z0-9.-]{2,62}")) {
            throw new IllegalStateException("Private object storage is not configured");
        }
        UUID operationId;
        try {
            if (object.objectKey() == null || !object.objectKey().startsWith("exports/")) {
                throw new IllegalArgumentException("Invalid private object key");
            }
            operationId = UUID.fromString(object.objectKey().substring("exports/".length()));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid private object key", invalid);
        }
        if (operationId.version() != 7
                || operationId.variant() != 2
                || !object.objectKey().equals("exports/" + operationId)
                || object.sizeBytes() <= 0
                || object.sizeBytes() > MAX_BYTES
                || object.sha256() == null
                || !object.sha256().matches("[A-Za-z0-9+/]{43}=")
                || object.filename() == null
                || !object.filename().matches("[A-Za-z0-9._-]{1,128}")
                || !validMediaType(object)) {
            throw new IllegalArgumentException("Invalid confirmed private object metadata");
        }
        try (ResponseInputStream<GetObjectResponse> input = s3.getObject(GetObjectRequest.builder()
                .bucket(configuredBucket.get())
                .key(object.objectKey())
                .build())) {
            Long reported = input.response().contentLength();
            if (reported != null && reported != object.sizeBytes()) {
                throw new IllegalStateException("Private export object size mismatch");
            }
            byte[] bytes = input.readNBytes((int) object.sizeBytes() + 1);
            if (bytes.length != object.sizeBytes()
                    || !Base64.getEncoder().encodeToString(sha256(bytes)).equals(object.sha256())) {
                throw new IllegalStateException("Private export object checksum or size mismatch");
            }
            return bytes;
        } catch (IOException | software.amazon.awssdk.core.exception.SdkException unreadable) {
            throw new IllegalStateException("Private export object read failed", unreadable);
        }
    }

    private static boolean validMediaType(DownloadTicketService.ConfirmedObject object) {
        return ("application/pdf".equals(object.mediaType())
                        && object.filename().endsWith(".pdf"))
                || ("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(object.mediaType())
                        && object.filename().endsWith(".xlsx"))
                || ("application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(object.mediaType())
                        && object.filename().endsWith(".docx"))
                || ("application/xml".equals(object.mediaType())
                        && object.filename().endsWith(".xml"));
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
