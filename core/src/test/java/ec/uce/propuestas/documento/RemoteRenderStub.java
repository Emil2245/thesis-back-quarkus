package ec.uce.propuestas.documento;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Isolated test-only HTTP peer; it never starts Documentos or uses production keys. */
public class RemoteRenderStub implements QuarkusTestResourceLifecycleManager {
    static final AtomicInteger calls = new AtomicInteger();
    static final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    static final AtomicReference<String> lastProjection = new AtomicReference<>();
    private HttpServer server;
    private Path privateKey;

    @Override
    public Map<String, String> start() {
        try {
            var pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            privateKey = Files.createTempFile(Path.of("build"), "documentos-test-service-", ".pem");
            Files.writeString(
                    privateKey,
                    "-----BEGIN PRIVATE KEY-----\n"
                            + Base64.getMimeEncoder(64, new byte[] {'\n'})
                                    .encodeToString(pair.getPrivate().getEncoded())
                            + "\n-----END PRIVATE KEY-----\n",
                    StandardCharsets.US_ASCII);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/v1/render", exchange -> {
                calls.incrementAndGet();
                lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                String json = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                lastProjection.set(json);
                byte[] result =
                        json.contains("\"formato\":\"pdf\"") ? new byte[] {'%', 'P', 'D', 'F'} : new byte[] {'P', 'K'};
                exchange.sendResponseHeaders(200, result.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(result);
                }
            });
            server.start();
            return Map.of(
                    "documentos.base-url",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "documentos.service.signing-key-location",
                    privateKey.toAbsolutePath().toString(),
                    "documentos.test-local-render",
                    "false");
        } catch (Exception e) {
            throw new IllegalStateException("Could not start isolated Documentos stub", e);
        }
    }

    @Override
    public void stop() {
        if (server != null) server.stop(0);
        if (privateKey != null) {
            try {
                Files.deleteIfExists(privateKey);
            } catch (Exception ignored) {
                // A failed test cleanup must not obscure the assertion result.
            }
        }
    }
}
