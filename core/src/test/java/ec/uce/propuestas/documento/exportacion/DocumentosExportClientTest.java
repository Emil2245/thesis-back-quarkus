package ec.uce.propuestas.documento.exportacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1;
import ec.uce.propuestas.documentos.contract.ExportUploadV1;
import ec.uce.propuestas.documentos.contract.SpecificationProjectionV1;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DocumentosExportClientTest {
    private static final UUID OPERATION = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final String FINGERPRINT = "a".repeat(64);
    private static final String MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String CHECKSUM = "A".repeat(43) + "=";
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void uploadsOnlyBoundProjectionAndAcceptsConfirmedPrivateMetadata() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/internal/v1/export", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            assertEquals(
                    "Bearer service-" + OPERATION, exchange.getRequestHeaders().getFirst("Authorization"));
            var request = json.readTree(exchange.getRequestBody());
            assertEquals(OPERATION.toString(), request.path("operationId").asText());
            assertEquals(FINGERPRINT, request.path("fingerprint").asText());
            assertEquals("exports/" + OPERATION, request.path("key").asText());
            assertEquals(
                    "A-1",
                    request.path("specification")
                            .path("especificaciones")
                            .get(0)
                            .path("codigo")
                            .asText());
            respond(exchange, 200, validResponse());
        });
        server.start();
        var stored = client().upload(request());
        assertEquals("exports/" + OPERATION, stored.key());
        assertEquals(CHECKSUM, stored.checksumSha256());
        assertEquals(123, stored.size());
        assertEquals(MIME, stored.mimeType());
    }

    @Test
    void uncertainErrorDoesNotLeakResponseAndCanReconcileWithoutAnotherPut() throws Exception {
        var puts = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/internal/v1/export/reconcile", exchange -> respond(exchange, 200, validResponse()));
        server.createContext("/internal/v1/export", exchange -> {
            puts.incrementAndGet();
            respond(exchange, 503, "sensitive internal error");
        });
        server.start();
        var client = client();
        var uncertain =
                assertThrows(DocumentosExportClient.UploadUncertainException.class, () -> client.upload(request()));
        assertFalse(uncertain.getMessage().contains("sensitive"));
        assertEquals(CHECKSUM, client.reconcile(request()).checksumSha256());
        assertEquals(1, puts.get());
    }

    @Test
    void rejectsResponseWithWrongObjectBeforeReportingUploadSuccess() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(
                "/internal/v1/export",
                exchange -> respond(
                        exchange,
                        200,
                        validResponse()
                                .replace("exports/" + OPERATION, "exports/01900000-0000-7000-8000-000000000009")));
        server.start();
        var failure = assertThrows(IllegalStateException.class, () -> client().upload(request()));
        assertTrue(failure.getMessage().contains("metadata"));
    }

    @Test
    void treatsNullSuccessfulBodyAsUncertainAndNeverAsVerifiedUpload() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/internal/v1/export", exchange -> respond(exchange, 200, "null"));
        server.start();
        var unknown =
                assertThrows(DocumentosExportClient.UploadUncertainException.class, () -> client().upload(request()));
        assertTrue(unknown.getMessage().contains("metadata"));
    }

    @Test
    void treatsTimeoutThrottleOrUnexpectedSuccessCodeAsUncertain() throws Exception {
        var status = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/internal/v1/export", exchange -> respond(exchange, status.get(), "late outcome"));
        server.start();
        for (int code : new int[] {408, 429, 202}) {
            status.set(code);
            assertThrows(DocumentosExportClient.UploadUncertainException.class, () -> client().upload(request()));
        }
    }

    @Test
    void requiresTlsExceptForExplicitLoopbackTestEndpoint() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DocumentosExportClient(
                        URI.create("http://documentos.internal:8081"),
                        json,
                        id -> "service-token",
                        Duration.ofSeconds(2)));
        new DocumentosExportClient(
                URI.create("https://documentos.internal"), json, id -> "service-token", Duration.ofSeconds(2));
    }

    @Test
    void acceptsHttpOnlyForExplicitlyListedInternalHosts() {
        var allowed = Set.of("propuestas-documentos");
        new DocumentosExportClient(
                URI.create("http://propuestas-documentos:8081"),
                json,
                id -> "service-token",
                Duration.ofSeconds(2),
                allowed);
        new DocumentosExportClient(
                URI.create("http://PROPUESTAS-Documentos:8081"),
                json,
                id -> "service-token",
                Duration.ofSeconds(2),
                allowed);
        assertThrows(
                IllegalArgumentException.class,
                () -> new DocumentosExportClient(
                        URI.create("http://documentos.internal:8081"),
                        json,
                        id -> "service-token",
                        Duration.ofSeconds(2),
                        allowed));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DocumentosExportClient(
                        URI.create("http://propuestas-documentos.evil:8081"),
                        json,
                        id -> "service-token",
                        Duration.ofSeconds(2),
                        allowed));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DocumentosExportClient(
                        URI.create("http://user:pass@propuestas-documentos:8081"),
                        json,
                        id -> "service-token",
                        Duration.ofSeconds(2),
                        allowed));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DocumentosExportClient(
                        URI.create("ftp://propuestas-documentos:8081"),
                        json,
                        id -> "service-token",
                        Duration.ofSeconds(2),
                        allowed));
    }

    @Test
    void emptyAllowedHttpHostsKeepsRejectingInternalHttpHosts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DocumentosExportClient(
                        URI.create("http://propuestas-documentos:8081"),
                        json,
                        id -> "service-token",
                        Duration.ofSeconds(2),
                        Set.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DocumentosExportClient(
                        URI.create("http://propuestas-documentos:8081"),
                        json,
                        id -> "service-token",
                        Duration.ofSeconds(2)));
    }

    @Test
    void cronogramaFormatsBindFilenameAndReturnedMime() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/internal/v1/export", exchange -> {
            var body = json.readTree(exchange.getRequestBody());
            String format = body.path("cronograma").path("format").asText();
            String mime = switch (format) {
                case "mspdi" -> "application/xml";
                case "pdf" -> "application/pdf";
                default -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            };
            respond(
                    exchange,
                    200,
                    "{\"key\":\"exports/" + OPERATION + "\",\"size\":123,\"checksumSha256\":\"" + CHECKSUM
                            + "\",\"mimeType\":\"" + mime + "\",\"filename\":\""
                            + body.path("filename").asText() + "\"}");
        });
        server.start();
        for (String format : List.of("xlsx", "pdf", "mspdi")) {
            var projection = cronograma(format);
            String filename = "schedule." + (format.equals("mspdi") ? "xml" : format);
            var request = new ExportUploadV1(
                    1, "exports/" + OPERATION, OPERATION.toString(), FINGERPRINT, filename, null, null, projection);
            assertEquals(filename, client().upload(request).filename());
            var wrong = new ExportUploadV1(
                    1, request.key(), request.operationId(), FINGERPRINT, "wrong.docx", null, null, projection);
            assertThrows(IllegalArgumentException.class, () -> client().upload(wrong));
            var ambiguous = new ExportUploadV1(
                    1,
                    request.key(),
                    request.operationId(),
                    FINGERPRINT,
                    filename,
                    null,
                    request().specification(),
                    projection);
            assertThrows(IllegalArgumentException.class, () -> client().upload(ambiguous));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> client().upload(new ExportUploadV1(
                        1, "exports/" + OPERATION, OPERATION.toString(), FINGERPRINT, "none.pdf", null, null, null)));
    }

    private static CronogramaProjectionV1 cronograma(String format) {
        if (format.equals("mspdi"))
            return new CronogramaProjectionV1(
                    1,
                    format,
                    null,
                    null,
                    new CronogramaProjectionV1.Mspdi(
                            "id", "code", "project", 2026, "month", 1, "2026-01-01", List.of()));
        var totals = new CronogramaProjectionV1.Totals(List.of("0"), List.of("0"), List.of("0"), List.of("0"));
        var sheet = new CronogramaProjectionV1.Sheet(
                "code", "project", 2026, "month", 1, "2026-01-01", "i", "s", 2, 2, List.of(), List.of(), totals);
        return new CronogramaProjectionV1(1, format, format.equals("pdf") ? "a4" : null, sheet, null);
    }

    private DocumentosExportClient client() {
        return new DocumentosExportClient(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                json,
                id -> "service-" + id,
                Duration.ofSeconds(2));
    }

    private static ExportUploadV1 request() {
        var spec = new SpecificationProjectionV1(
                1,
                OPERATION,
                "Project",
                (short) 2026,
                "Technical",
                "Specifications",
                "2026-11-08",
                List.of(new SpecificationProjectionV1.Specification(
                        "A-1", "Concrete", "m3", "Concrete specification")));
        return new ExportUploadV1(
                1, "exports/" + OPERATION, OPERATION.toString(), FINGERPRINT, "technical.docx", null, spec);
    }

    private static String validResponse() {
        return "{\"key\":\"exports/" + OPERATION + "\",\"size\":123,\"checksumSha256\":\"" + CHECKSUM
                + "\",\"mimeType\":\"" + MIME + "\",\"filename\":\"technical.docx\"}";
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
