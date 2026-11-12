package ec.uce.propuestas.documento.exportacion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

class CorePrivateObjectReaderTest {
    private static final byte[] BYTES = "private export".getBytes(StandardCharsets.UTF_8);
    private HttpServer server;
    private S3Client s3;

    @AfterEach
    void stop() {
        if (s3 != null) s3.close();
        if (server != null) server.stop(0);
    }

    @Test
    void readsOnlyExpectedPrivateKeyAndChecksLengthAndSha256() throws Exception {
        var calls = new AtomicInteger();
        serve(BYTES, calls);
        var reader = new CorePrivateObjectReader(s3, "private-test");
        assertArrayEquals(BYTES, reader.read(object(BYTES.length, checksum(BYTES))));
        assertFalse(calls.get() == 0);
        var mismatch = assertThrows(
                IllegalStateException.class,
                () -> reader.read(object(BYTES.length, Base64.getEncoder().encodeToString(new byte[32]))));
        assertFalse(mismatch.getMessage().contains("private-test"));
        assertThrows(IllegalStateException.class, () -> reader.read(object(BYTES.length - 1, checksum(BYTES))));
    }

    @Test
    void refusesMissingBucketAndInvalidKeyBeforeNetworkRead() throws Exception {
        var calls = new AtomicInteger();
        serve(BYTES, calls);
        assertThrows(
                IllegalStateException.class,
                () -> new CorePrivateObjectReader(s3, "").read(object(BYTES.length, checksum(BYTES))));
        var invalid = new DownloadTicketService.ConfirmedObject(
                "other/key", checksum(BYTES), BYTES.length, "application/pdf", "budget.pdf");
        assertThrows(
                IllegalArgumentException.class, () -> new CorePrivateObjectReader(s3, "private-test").read(invalid));
        assertFalse(calls.get() > 0);
    }

    @Test
    void refusesOversizedMetadataAndExtraBytesEvenWhenS3ReportsExpectedLength() throws Exception {
        var calls = new AtomicInteger();
        S3Client stub = (S3Client) Proxy.newProxyInstance(
                S3Client.class.getClassLoader(), new Class<?>[] {S3Client.class}, (proxy, method, arguments) -> {
                    calls.incrementAndGet();
                    return new ResponseInputStream<>(
                            GetObjectResponse.builder().contentLength(1L).build(),
                            AbortableInputStream.create(new ByteArrayInputStream(BYTES)));
                });
        var reader = new CorePrivateObjectReader(stub, "private-test");
        assertThrows(IllegalArgumentException.class, () -> reader.read(object(64L * 1024 * 1024 + 1, checksum(BYTES))));
        assertEquals(0, calls.get());
        assertThrows(IllegalStateException.class, () -> reader.read(object(1, checksum(BYTES))));
        assertEquals(1, calls.get());
    }

    @Test
    void acceptsOnlyMatchingSupportedMediaTypesAndExtensions() throws Exception {
        var calls = new AtomicInteger();
        serve(BYTES, calls);
        var reader = new CorePrivateObjectReader(s3, "private-test");
        String[][] supported = {
            {"application/xml", "schedule.xml"},
            {"application/pdf", "budget.pdf"},
            {"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "budget.xlsx"},
            {"application/vnd.openxmlformats-officedocument.wordprocessingml.document", "spec.docx"}
        };
        for (String[] format : supported) {
            assertArrayEquals(BYTES, reader.read(formattedObject(format[0], format[1])));
        }
        int reads = calls.get();
        assertThrows(
                IllegalArgumentException.class, () -> reader.read(formattedObject("application/xml", "schedule.pdf")));
        assertThrows(
                IllegalArgumentException.class, () -> reader.read(formattedObject("application/pdf", "schedule.xml")));
        assertThrows(IllegalArgumentException.class, () -> reader.read(formattedObject("text/xml", "schedule.xml")));
        assertEquals(reads, calls.get());
    }

    private static DownloadTicketService.ConfirmedObject formattedObject(String mediaType, String filename)
            throws Exception {
        return new DownloadTicketService.ConfirmedObject(
                "exports/01900000-0000-7000-8000-000000000001", checksum(BYTES), BYTES.length, mediaType, filename);
    }

    private void serve(byte[] bytes, AtomicInteger calls) throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/private-test/exports/01900000-0000-7000-8000-000000000001", exchange -> {
            calls.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.start();
        s3 = S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(
                        URI.create("http://127.0.0.1:" + server.getAddress().getPort()))
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    private static DownloadTicketService.ConfirmedObject object(long size, String sha) {
        return new DownloadTicketService.ConfirmedObject(
                "exports/01900000-0000-7000-8000-000000000001", sha, size, "application/pdf", "budget.pdf");
    }

    private static String checksum(byte[] bytes) throws Exception {
        return Base64.getEncoder()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
