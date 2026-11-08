package ec.uce.propuestas.documentos.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3PrivateObjectStoreTest {
    private static final byte[] CONTENT = "private export".getBytes(StandardCharsets.UTF_8);
    private static final String KEY = "exports/01900000-0000-7000-8000-000000000001";
    private static final String FINGERPRINT = "a".repeat(64);

    @Test
    void confirmsConditionalPutWithHeadChecksumBeforeReportingSuccess() throws Exception {
        var puts = new AtomicInteger();
        String checksum = Base64.getEncoder()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(CONTENT));
        S3Client s3 = stub((method, arguments) -> {
            if ("putObject".equals(method)) {
                PutObjectRequest request = (PutObjectRequest) arguments[0];
                RequestBody body = (RequestBody) arguments[1];
                assertEquals("*", request.ifNoneMatch());
                assertEquals(checksum, request.checksumSHA256());
                assertEquals(FINGERPRINT, request.metadata().get("fingerprint"));
                assertEquals("budget.pdf", request.metadata().get("filename"));
                assertEquals("application/pdf", request.contentType());
                assertEquals(CONTENT.length, body.optionalContentLength().orElseThrow());
                puts.incrementAndGet();
                return PutObjectResponse.builder().build();
            }
            if ("headObject".equals(method)) {
                HeadObjectRequest request = (HeadObjectRequest) arguments[0];
                assertEquals(KEY, request.key());
                return HeadObjectResponse.builder()
                        .contentLength((long) CONTENT.length)
                        .checksumSHA256(checksum)
                        .contentType("application/pdf")
                        .metadata(Map.of("fingerprint", FINGERPRINT, "filename", "budget.pdf"))
                        .build();
            }
            throw new AssertionError("Unexpected S3 call: " + method);
        });
        var result = new S3PrivateObjectStore(s3, "private-test-bucket")
                .store(KEY, CONTENT, "application/pdf", "budget.pdf", FINGERPRINT);
        assertEquals(1, puts.get());
        assertEquals(KEY, result.key());
        assertEquals(checksum, result.checksumSha256());
        assertEquals(CONTENT.length, result.size());
    }

    @Test
    void rejectsPreexistingObjectEvenIfItHasTheSameKey() {
        S3Client s3 = stub((method, arguments) -> {
            if ("putObject".equals(method)) {
                throw S3Exception.builder()
                        .statusCode(412)
                        .message("Already exists")
                        .build();
            }
            throw new AssertionError("Must not confirm a conflicting object");
        });
        assertThrows(
                IllegalStateException.class,
                () -> new S3PrivateObjectStore(s3, "private-test-bucket")
                        .store(KEY, CONTENT, "application/pdf", "budget.pdf", FINGERPRINT));
    }

    @Test
    void rejectsUnconfirmedHeadWithoutDeclaringSuccess() {
        S3Client s3 = stub((method, arguments) -> {
            if ("putObject".equals(method)) {
                return PutObjectResponse.builder().build();
            }
            if ("headObject".equals(method)) {
                return HeadObjectResponse.builder()
                        .contentLength(3L)
                        .checksumSHA256("wrong")
                        .contentType("application/pdf")
                        .metadata(Map.of("fingerprint", FINGERPRINT, "filename", "budget.pdf"))
                        .build();
            }
            throw new AssertionError("Unexpected S3 call: " + method);
        });
        var failure = assertThrows(
                IllegalStateException.class,
                () -> new S3PrivateObjectStore(s3, "private-test-bucket")
                        .store(KEY, CONTENT, "application/pdf", "budget.pdf", FINGERPRINT));
        assertTrue(failure.getMessage().contains("confirm"));
    }

    @Test
    void rejectsHeadWithUnconfirmedMimeOrFilename() throws Exception {
        String checksum = Base64.getEncoder()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(CONTENT));
        S3Client s3 = stub((method, arguments) -> {
            if ("headObject".equals(method)) {
                return HeadObjectResponse.builder()
                        .contentLength((long) CONTENT.length)
                        .checksumSHA256(checksum)
                        .contentType("text/plain")
                        .metadata(Map.of("fingerprint", FINGERPRINT, "filename", "other.pdf"))
                        .build();
            }
            throw new AssertionError("Recovery must not write");
        });
        assertThrows(
                IllegalStateException.class,
                () -> new S3PrivateObjectStore(s3, "private-test-bucket")
                        .confirm(KEY, CONTENT, "application/pdf", "budget.pdf", FINGERPRINT));
    }

    @Test
    void reconcilesUncertainPutOnlyWhenHeadStillMatches() throws Exception {
        var puts = new AtomicInteger();
        String checksum = Base64.getEncoder()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(CONTENT));
        S3Client s3 = stub((method, arguments) -> {
            if ("putObject".equals(method)) {
                puts.incrementAndGet();
                throw S3Exception.builder()
                        .statusCode(503)
                        .message("Connection lost after write")
                        .build();
            }
            if ("headObject".equals(method)) {
                return HeadObjectResponse.builder()
                        .contentLength((long) CONTENT.length)
                        .checksumSHA256(checksum)
                        .contentType("application/pdf")
                        .metadata(Map.of("fingerprint", FINGERPRINT, "filename", "budget.pdf"))
                        .build();
            }
            throw new AssertionError("Unexpected S3 call: " + method);
        });
        var storage = new S3PrivateObjectStore(s3, "private-test-bucket");
        assertThrows(
                S3Exception.class, () -> storage.store(KEY, CONTENT, "application/pdf", "budget.pdf", FINGERPRINT));
        var recovered = storage.confirm(KEY, CONTENT, "application/pdf", "budget.pdf", FINGERPRINT);
        assertEquals(KEY, recovered.key());
        assertEquals(checksum, recovered.checksumSha256());
        assertEquals(1, puts.get());
    }

    @Test
    void rejectsInvalidObjectIdentityBeforeCallingS3() {
        S3Client s3 = stub((method, arguments) -> {
            throw new AssertionError("Invalid identity must not reach S3");
        });
        var storage = new S3PrivateObjectStore(s3, "private-test-bucket");
        assertThrows(
                IllegalArgumentException.class,
                () -> storage.store(
                        "exports/------------------------------------",
                        CONTENT,
                        "application/pdf",
                        "budget.pdf",
                        FINGERPRINT));
        assertThrows(
                IllegalArgumentException.class,
                () -> storage.store(KEY, CONTENT, "application/pdf", "budget.pdf", "x"));
        assertThrows(
                IllegalArgumentException.class,
                () -> storage.store(KEY, CONTENT, "application/pdf", "../budget.pdf", FINGERPRINT));
    }

    private static S3Client stub(S3Call call) {
        return (S3Client) Proxy.newProxyInstance(
                S3Client.class.getClassLoader(),
                new Class<?>[] {S3Client.class},
                (proxy, method, arguments) -> call.apply(method.getName(), arguments));
    }

    @FunctionalInterface
    private interface S3Call {
        Object apply(String method, Object[] arguments);
    }
}
