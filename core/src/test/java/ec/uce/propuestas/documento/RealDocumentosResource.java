package ec.uce.propuestas.documento;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Starts the actual packaged Documentos app; the database belongs to Core's test Dev Service. */
public class RealDocumentosResource implements QuarkusTestResourceLifecycleManager {
    private Process child;
    private Path keys;

    @Override
    public Map<String, String> start() {
        if (!"true".equals(System.getenv("STAGE02_REAL_DOCUMENTOS"))) return Map.of();
        try {
            Path jar = Path.of("documentos/build/quarkus-app/quarkus-run.jar").toAbsolutePath();
            if (!Files.isRegularFile(jar)) throw new IllegalStateException("Build :documentos:quarkusBuild first");
            keys = Files.createTempDirectory(Path.of("build"), "real-documentos-");
            var pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            Path privateKey = keys.resolve("service-private.pem");
            Path publicKey = keys.resolve("service-public.pem");
            Files.writeString(
                    privateKey, pem("PRIVATE KEY", pair.getPrivate().getEncoded()), StandardCharsets.US_ASCII);
            Files.writeString(publicKey, pem("PUBLIC KEY", pair.getPublic().getEncoded()), StandardCharsets.US_ASCII);
            int port;
            try (var socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
                port = socket.getLocalPort();
            }
            var process = new ProcessBuilder(
                            Path.of(System.getProperty("java.home"), "bin", "java")
                                    .toString(),
                            "-jar",
                            jar.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(keys.resolve("documentos.log").toFile());
            process.environment().put("QUARKUS_HTTP_HOST", "127.0.0.1");
            process.environment().put("QUARKUS_HTTP_PORT", String.valueOf(port));
            process.environment()
                    .put(
                            "MP_JWT_VERIFY_PUBLICKEY_LOCATION",
                            publicKey.toAbsolutePath().toString());
            child = process.start();
            URI health = URI.create("http://127.0.0.1:" + port + "/q/health");
            long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
            while (System.nanoTime() < deadline && child.isAlive()) {
                try {
                    var connection = (HttpURLConnection) health.toURL().openConnection();
                    connection.setConnectTimeout(500);
                    connection.setReadTimeout(500);
                    if (connection.getResponseCode() == 200) {
                        connection.disconnect();
                        return Map.of(
                                "documentos.base-url",
                                "http://127.0.0.1:" + port,
                                "documentos.service.signing-key-location",
                                privateKey.toAbsolutePath().toString(),
                                "documentos.test-local-render",
                                "false");
                    }
                    connection.disconnect();
                } catch (java.io.IOException ignored) {
                    // The real app is still starting; fail closed on exit or deadline.
                }
                Thread.sleep(200);
            }
            throw new IllegalStateException(
                    "Packaged Documentos did not become healthy; see " + keys.resolve("documentos.log"));
        } catch (Exception e) {
            stop();
            throw new IllegalStateException("Could not start packaged Documentos", e);
        }
    }

    private static String pem(String label, byte[] encoded) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded)
                + "\n-----END " + label + "-----\n";
    }

    @Override
    public void stop() {
        if (child != null) {
            child.destroy();
            try {
                if (!child.waitFor(5, TimeUnit.SECONDS)) {
                    child.destroyForcibly();
                    child.waitFor(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                child.destroyForcibly();
            }
            if (child.isAlive()) {
                System.err.println("Documentos test process could not be stopped; temporary keys retained at " + keys);
                return;
            }
        }
        if (keys != null) {
            for (String file : new String[] {"service-private.pem", "service-public.pem", "documentos.log"}) {
                try {
                    Files.deleteIfExists(keys.resolve(file));
                } catch (java.io.IOException failure) {
                    System.err.println("Could not remove Documentos test artifact: " + keys.resolve(file));
                }
            }
            try {
                Files.deleteIfExists(keys);
            } catch (java.io.IOException failure) {
                System.err.println("Could not remove Documentos test directory: " + keys);
            }
        }
    }
}
