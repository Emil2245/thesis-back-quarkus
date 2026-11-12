package ec.uce.propuestas.documento.exportacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import ec.uce.propuestas.documentos.contract.SpecificationProjectionV1;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/** Bounded internal transport: no entity, user bearer token or database session crosses this call. */
public final class DocumentosHttpClient {
    public static final int MAX_REQUEST_BYTES = DocumentosLimites.MAX_REQUEST_BYTES;
    public static final int MAX_RESPONSE_BYTES = DocumentosLimites.MAX_RESPONSE_BYTES;

    private final URI endpoint;
    private final ObjectMapper json;
    private final Supplier<String> serviceToken;
    private final Duration timeout;
    private final int maxResponseBytes;
    private final HttpClient client;

    public DocumentosHttpClient(URI baseUri, ObjectMapper json, Supplier<String> serviceToken, Duration timeout) {
        this(baseUri, json, serviceToken, timeout, MAX_RESPONSE_BYTES);
    }

    public DocumentosHttpClient(
            URI baseUri, ObjectMapper json, Supplier<String> serviceToken, Duration timeout, int maxResponseBytes) {
        Objects.requireNonNull(baseUri, "baseUri");
        if (!("http".equals(baseUri.getScheme()) || "https".equals(baseUri.getScheme()))
                || baseUri.getHost() == null
                || baseUri.getUserInfo() != null) {
            throw new IllegalArgumentException("Invalid internal Documentos base URI");
        }
        this.endpoint = baseUri.resolve("/internal/v1/render");
        this.json = Objects.requireNonNull(json, "json");
        this.serviceToken = Objects.requireNonNull(serviceToken, "serviceToken");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Invalid Documentos timeout");
        if (maxResponseBytes <= 0 || maxResponseBytes > MAX_RESPONSE_BYTES)
            throw new IllegalArgumentException("Invalid Documentos response limit");
        this.maxResponseBytes = maxResponseBytes;
        this.client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    public byte[] render(DocumentProjectionV1 projection) {
        return send(projection, endpoint);
    }

    public byte[] renderSpecifications(SpecificationProjectionV1 projection) {
        return send(projection, endpoint.resolve("/internal/v1/render/specifications"));
    }

    private byte[] send(Object projection, URI target) {
        Objects.requireNonNull(projection, "projection");
        byte[] payload;
        try {
            payload = json.writeValueAsBytes(projection);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid document projection", e);
        }
        if (payload.length > MAX_REQUEST_BYTES) throw new IllegalArgumentException("Document projection exceeds 8 MiB");
        String token = serviceToken.get();
        if (token == null || token.isBlank()) throw new IllegalStateException("Dedicated service token is unavailable");
        var request = HttpRequest.newBuilder(target)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();
        try {
            // JDK 25 cancels upstream when this limit is exceeded; the body never buffers without a cap.
            var response = client.send(
                    request,
                    HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), maxResponseBytes));
            if (response.statusCode() != 200 || response.body() == null || response.body().length == 0) {
                throw new IllegalStateException("Documentos render failed (HTTP " + response.statusCode() + ")");
            }
            return response.body();
        } catch (HttpTimeoutException e) {
            throw new IllegalStateException("Documentos render request timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Documentos render request interrupted");
        } catch (IOException e) {
            throw new IllegalStateException("Documentos render response failed or exceeds configured limit");
        }
    }
}
