package ec.uce.propuestas.documento.exportacion;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

/** Delete-only adapter for a separately identified cleanup workload, never a public Core route. */
@ApplicationScoped
public class PrivateObjectDeleter {
    @Inject
    S3Client s3;

    @ConfigProperty(name = "cleanup.storage.bucket")
    Optional<String> configuredBucket;

    public PrivateObjectDeleter() {}

    PrivateObjectDeleter(S3Client s3, String bucket) {
        this.s3 = Objects.requireNonNull(s3, "s3");
        this.configuredBucket = Optional.ofNullable(bucket);
    }

    public void delete(ExportCleanupLeaseService.Claim claim) {
        String bucket =
                configuredBucket.orElseThrow(() -> new IllegalStateException("Cleanup bucket is not configured"));
        if (!bucket.matches("[A-Za-z0-9][A-Za-z0-9.-]{2,62}")) {
            throw new IllegalStateException("Cleanup bucket is invalid");
        }
        if (claim == null
                || claim.operationId() == null
                || claim.operationId().version() != 7
                || claim.operationId().variant() != 2
                || claim.fence() <= 0
                || !("exports/" + claim.operationId()).equals(claim.objectKey())) {
            throw new IllegalArgumentException("Invalid cleanup claim");
        }
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(claim.objectKey())
                .build());
    }
}
