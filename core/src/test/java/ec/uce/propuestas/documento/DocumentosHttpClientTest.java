package ec.uce.propuestas.documento;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import ec.uce.propuestas.documento.exportacion.DocumentoContratoMapper;
import ec.uce.propuestas.documento.exportacion.DocumentosHttpClient;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import ec.uce.propuestas.documentos.contract.SpecificationProjectionV1;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DocumentosHttpClientTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void postsOnlyPublicProjectionAndServiceTokenAndReturnsExactBytes() throws Exception {
        byte[] expected = new byte[] {0, 1, 2, 3, -1};
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/internal/v1/render", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            assertEquals(
                    "Bearer dedicated-service-token",
                    exchange.getRequestHeaders().getFirst("Authorization"));
            var body = json.readTree(exchange.getRequestBody());
            assertEquals(1, body.path("schemaVersion").asInt());
            assertEquals("presupuesto", body.path("documento").asText());
            assertEquals(
                    "01900000-0000-7000-8000-000000000030",
                    body.path("snapshot").path("presupuestoId").asText());
            assertFalse(body.path("snapshot").has("preflight"));
            exchange.sendResponseHeaders(200, expected.length);
            try (var out = exchange.getResponseBody()) {
                out.write(expected);
            }
        });
        server.start();

        assertArrayEquals(expected, client(Duration.ofSeconds(2)).render(request()));
    }

    @Test
    void postsTechnicalDocxToDedicatedPathWithSameBoundsAndToken() throws Exception {
        byte[] expected = new byte[] {'P', 'K', 3, 4};
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/internal/v1/render/specifications", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            assertEquals(
                    "Bearer dedicated-service-token",
                    exchange.getRequestHeaders().getFirst("Authorization"));
            var body = json.readTree(exchange.getRequestBody());
            assertEquals(
                    "A-1", body.path("especificaciones").get(0).path("codigo").asText());
            assertFalse(body.has("proyectoInternoId"));
            exchange.sendResponseHeaders(200, expected.length);
            try (var out = exchange.getResponseBody()) {
                out.write(expected);
            }
        });
        server.start();
        var projection = new SpecificationProjectionV1(
                1,
                UUID.fromString("01900000-0000-7000-8000-000000000001"),
                "Proyecto",
                (short) 2026,
                "Título",
                "Subtítulo",
                "2026-11-07",
                List.of(new SpecificationProjectionV1.Specification("A-1", "Losa", "u", "Texto técnico")));

        assertArrayEquals(expected, client(Duration.ofSeconds(2)).renderSpecifications(projection));
    }

    @Test
    void rejectsOversizedProjectionWithoutCallingDocumentos() throws Exception {
        var calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/internal/v1/render", exchange -> calls.incrementAndGet());
        server.start();
        var request = request();
        var original = request.snapshot();
        var oversized = new DocumentProjectionV1(
                request.schemaVersion(),
                request.documento(),
                request.formato(),
                request.opciones(),
                new DocumentProjectionV1.Snapshot(
                        original.presupuestoId(),
                        original.version(),
                        original.vigente(),
                        "x".repeat(8 * 1024 * 1024),
                        original.total(),
                        original.proyecto(),
                        original.parametros(),
                        original.display(),
                        original.firmantes(),
                        original.capitulos(),
                        original.apus()));

        assertThrows(
                IllegalArgumentException.class,
                () -> client(Duration.ofSeconds(2)).render(oversized));
        assertEquals(0, calls.get());
    }

    @Test
    void rejectsRemoteErrorWithoutLeakingItsBody() throws Exception {
        byte[] sensitive = "internal details".getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/internal/v1/render", exchange -> {
            exchange.sendResponseHeaders(503, sensitive.length);
            try (var out = exchange.getResponseBody()) {
                out.write(sensitive);
            }
        });
        server.start();

        var error = assertThrows(
                IllegalStateException.class, () -> client(Duration.ofSeconds(2)).render(request()));
        assertTrue(error.getMessage().contains("503"));
        assertFalse(error.getMessage().contains("internal details"));
    }

    @Test
    void capsChunkedResponseWithoutAContentLength() throws Exception {
        byte[] oversized = new byte[65];
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/internal/v1/render", exchange -> {
            exchange.sendResponseHeaders(200, 0); // chunked; no Content-Length header
            try (var out = exchange.getResponseBody()) {
                out.write(oversized);
            }
        });
        server.start();

        var error = assertThrows(
                IllegalStateException.class,
                () -> client(Duration.ofSeconds(2), 64).render(request()));
        assertTrue(error.getMessage().contains("exceeds"));
    }

    @Test
    void timesOutWithinTheConfiguredBound() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/internal/v1/render", exchange -> {
            try {
                Thread.sleep(1000);
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();

        long started = System.nanoTime();
        assertThrows(
                IllegalStateException.class,
                () -> client(Duration.ofMillis(100)).render(request()));
        assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(2)) < 0);
    }

    private DocumentosHttpClient client(Duration timeout) {
        return new DocumentosHttpClient(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                json,
                () -> "dedicated-service-token",
                timeout);
    }

    private DocumentosHttpClient client(Duration timeout, int responseLimit) {
        return new DocumentosHttpClient(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                json,
                () -> "dedicated-service-token",
                timeout,
                responseLimit);
    }

    private static DocumentProjectionV1 request() {
        return DocumentoContratoMapper.proyectar(
                PresupuestoDocumentoFixture.snapshot(2, 0),
                "presupuesto",
                PresupuestoDocumentoFixture.opciones("xlsx", false));
    }
}
