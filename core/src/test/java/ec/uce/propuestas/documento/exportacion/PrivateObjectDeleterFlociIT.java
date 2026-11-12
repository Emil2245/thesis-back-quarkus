package ec.uce.propuestas.documento.exportacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Opt-in SDK→isolated FLoCI check; creates and deletes only its own unique export key. */
class PrivateObjectDeleterFlociIT {
    @Test
    void deletesOnlyItsTaskCreatedObjectAndReportsMissingOnHead() {
        assumeTrue("true".equals(System.getenv("RUN_STAGE03_FLOCI_S3_TEST")));
        String endpoint = System.getenv("STAGE03_FLOCI_ENDPOINT");
        String bucket = System.getenv("STAGE03_FLOCI_BUCKET");
        assumeTrue(endpoint != null
                && endpoint.startsWith("http://127.0.0.1:")
                && bucket != null
                && bucket.startsWith("stage03-cond-"));
        UUID random = UUID.randomUUID();
        UUID operation =
                new UUID((random.getMostSignificantBits() & ~0xf000L) | 0x7000L, random.getLeastSignificantBits());
        var claim = new ExportCleanupLeaseService.Claim(operation, 1, "exports/" + operation);
        try (S3Client s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create("test-only", "test-only")))
                .forcePathStyle(true)
                .build()) {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(claim.objectKey())
                            .ifNoneMatch("*")
                            .build(),
                    RequestBody.fromBytes("isolated cleanup probe".getBytes(StandardCharsets.UTF_8)));
            var head = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(claim.objectKey())
                    .build();
            assertEquals("isolated cleanup probe".length(), s3.headObject(head).contentLength());
            var deleter = new PrivateObjectDeleter(s3, bucket);
            deleter.delete(claim);
            assertEquals(
                    404,
                    assertThrows(S3Exception.class, () -> s3.headObject(head)).statusCode());
            deleter.delete(claim); // S3 DeleteObject is idempotent after a lost response.
            assertEquals(
                    404,
                    assertThrows(S3Exception.class, () -> s3.headObject(head)).statusCode());
        }
    }
}
