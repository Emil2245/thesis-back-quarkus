package ec.uce.propuestas.documento;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import ec.uce.propuestas.documento.exportacion.CoreServiceJwt;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RemoteDocumentosRenderGatewayTest {
    @Test
    void sendsCapturedProjectionWithDedicatedServiceToken() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var calls = new AtomicInteger();
        server.createContext("/internal/v1/render", exchange -> {
            calls.incrementAndGet();
            assertEquals(
                    "Bearer service-test-token", exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals("application/json", exchange.getRequestHeaders().getFirst("Content-Type"));
            var json = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"documento\":\"presupuesto\""), json);
            assertTrue(json.contains("\"schemaVersion\":1"), json);
            var bytes = new byte[] {80, 75};
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        try {
            var gateway = new RemoteDocumentosRenderGateway();
            gateway.baseUrl =
                    Optional.of("http://127.0.0.1:" + server.getAddress().getPort());
            gateway.timeout = Duration.ofSeconds(5);
            gateway.json = new ObjectMapper().findAndRegisterModules();
            gateway.serviceJwt = new CoreServiceJwt() {
                @Override
                public String issueToken() {
                    return "service-test-token";
                }
            };
            var snapshot = PresupuestoDocumentoFixture.snapshot(2, 0);

            assertArrayEquals(
                    new byte[] {80, 75},
                    gateway.render(snapshot, "presupuesto", PresupuestoDocumentoFixture.opciones("xlsx", false)));
            assertEquals(1, calls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingEndpointFailsClosedBeforeSigningOrSending() {
        var gateway = new RemoteDocumentosRenderGateway();
        gateway.baseUrl = Optional.empty();
        assertThrows(
                IllegalStateException.class,
                () -> gateway.render(
                        PresupuestoDocumentoFixture.snapshot(2, 0),
                        "presupuesto",
                        PresupuestoDocumentoFixture.opciones("xlsx", false)));
    }
}
