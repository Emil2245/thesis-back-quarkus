package ec.uce.propuestas.documentos.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** Opt-in isolated FLoCI SDK check; never targets an implicit bucket or deletes shared objects. */
class S3PrivateObjectStoreFlociIT {
    @Test
    void writesConditionallyAndRecoversOnlyConfirmedPrivateObject() {
        assumeTrue("true".equals(System.getenv("RUN_STAGE03_FLOCI_S3_TEST")));
        String endpoint = System.getenv("STAGE03_FLOCI_ENDPOINT");
        String bucket = System.getenv("STAGE03_FLOCI_BUCKET");
        assumeTrue(endpoint != null
                && endpoint.startsWith("http://127.0.0.1:")
                && bucket != null
                && bucket.startsWith("stage03-cond-"));
        try (S3Client client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create("test-only", "test-only")))
                .forcePathStyle(true)
                .build()) {
            var storage = new S3PrivateObjectStore(client, bucket);
            UUID random = UUID.randomUUID();
            String key = "exports/"
                    + new UUID(
                            (random.getMostSignificantBits() & ~0xf000L) | 0x7000L, random.getLeastSignificantBits());
            String fingerprint = "a".repeat(64);
            byte[] bytes = "synthetic isolated document".getBytes(StandardCharsets.UTF_8);
            var uploaded = storage.store(key, bytes, "application/pdf", "synthetic.pdf", fingerprint);
            assertEquals(key, uploaded.key());
            assertEquals(bytes.length, uploaded.size());
            assertEquals(uploaded, storage.confirm(key, bytes, "application/pdf", "synthetic.pdf", fingerprint));
            assertThrows(
                    IllegalStateException.class,
                    () -> storage.store(
                            key,
                            "changed".getBytes(StandardCharsets.UTF_8),
                            "application/pdf",
                            "synthetic.pdf",
                            "b".repeat(64)));
            assertEquals(uploaded, storage.confirm(key, bytes, "application/pdf", "synthetic.pdf", fingerprint));
        }
    }
}
