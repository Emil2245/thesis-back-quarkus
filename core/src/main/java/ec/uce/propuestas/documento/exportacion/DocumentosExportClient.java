package ec.uce.propuestas.documento.exportacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.documentos.contract.ExportUploadV1;
import ec.uce.propuestas.documentos.contract.StoredObjectV1;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** Bounded Core→Documentos export transport; errors never expose the internal response body. */
public final class DocumentosExportClient {
    private static final int MAX_METADATA_BYTES = 8 * 1024;
    private final URI endpoint;
    private final ObjectMapper json;
    private final Function<UUID, String> serviceToken;
    private final Duration timeout;
    private final HttpClient client;

    public DocumentosExportClient(
            URI baseUri, ObjectMapper json, Function<UUID, String> serviceToken, Duration timeout) {
        Objects.requireNonNull(baseUri, "baseUri");
        String host = baseUri.getHost();
        boolean secure = "https".equals(baseUri.getScheme());
        boolean loopback = "http".equals(baseUri.getScheme())
                && ("127.0.0.1".equals(host) || "localhost".equals(host) || "[::1]".equals(host) || "::1".equals(host));
        if ((!secure && !loopback) || host == null || baseUri.getUserInfo() != null) {
            throw new IllegalArgumentException("Invalid internal Documentos base URI");
        }
        this.endpoint = baseUri.resolve("/internal/v1/export");
        this.json = Objects.requireNonNull(json, "json");
        this.serviceToken = Objects.requireNonNull(serviceToken, "serviceToken");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("Invalid Documentos timeout");
        this.client =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    public StoredObjectV1 upload(ExportUploadV1 request) {
        return send(request, endpoint);
    }

    public StoredObjectV1 reconcile(ExportUploadV1 request) {
        return send(request, endpoint.resolve("/internal/v1/export/reconcile"));
    }

    private StoredObjectV1 send(ExportUploadV1 request, URI target) {
        UUID operation = validate(request);
        byte[] payload;
        try {
            payload = json.writeValueAsBytes(request);
        } catch (IOException invalid) {
            throw new IllegalArgumentException("Invalid private export projection", invalid);
        }
        if (payload.length > DocumentosHttpClient.MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Document projection exceeds 8 MiB");
        }
        String token = serviceToken.apply(operation);
        if (token == null || token.isBlank()) throw new IllegalStateException("Dedicated service token is unavailable");
        var httpRequest = HttpRequest.newBuilder(target)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();
        try {
            var response = client.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), MAX_METADATA_BYTES));
            if (response.statusCode() != 200) {
                if (response.statusCode() == 400
                        || response.statusCode() == 401
                        || response.statusCode() == 403
                        || response.statusCode() == 404
                        || response.statusCode() == 413
                        || response.statusCode() == 422) {
                    throw new IllegalStateException("Documentos export rejected (HTTP " + response.statusCode() + ")");
                }
                throw new UploadUncertainException("Documentos export outcome is uncertain");
            }
            if (response.body() == null || response.body().length == 0) {
                throw new UploadUncertainException("Documentos export metadata is invalid");
            }
            StoredObjectV1 object;
            try {
                object = json.readValue(response.body(), StoredObjectV1.class);
            } catch (IOException invalid) {
                throw new UploadUncertainException("Documentos export metadata is invalid", invalid);
            }
            if (object == null
                    || !request.key().equals(object.key())
                    || object.size() <= 0
                    || object.size() > DocumentosHttpClient.MAX_RESPONSE_BYTES
                    || object.checksumSha256() == null
                    || !object.checksumSha256().matches("[A-Za-z0-9+/]{43}=")
                    || !request.filename().equals(object.filename())
                    || !expectedMime(request).equals(object.mimeType())) {
                throw new UploadUncertainException("Documentos export metadata is invalid");
            }
            return object;
        } catch (HttpTimeoutException timedOut) {
            throw new UploadUncertainException("Documentos export response timed out", timedOut);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new UploadUncertainException("Documentos export response interrupted", interrupted);
        } catch (IOException uncertain) {
            throw new UploadUncertainException(
                    "Documentos export response failed or exceeds configured limit", uncertain);
        }
    }

    private static UUID validate(ExportUploadV1 request) {
        if (request == null
                || request.schemaVersion() != ExportUploadV1.VERSION
                || request.operationId() == null
                || request.key() == null
                || request.fingerprint() == null
                || !request.fingerprint().matches("[0-9a-f]{64}")
                || request.filename() == null
                || !request.filename().matches("[A-Za-z0-9._-]{1,128}")
                || (request.projection() == null) == (request.specification() == null)) {
            throw new IllegalArgumentException("Invalid private export request");
        }
        UUID operation;
        try {
            operation = UUID.fromString(request.operationId());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid private export operation ID", invalid);
        }
        if (operation.version() != 7
                || operation.variant() != 2
                || !operation.toString().equals(request.operationId())
                || !request.key().equals("exports/" + operation)
                || !request.filename()
                        .endsWith(
                                request.projection() != null
                                        ? "." + request.projection().formato()
                                        : ".docx")) {
            throw new IllegalArgumentException("Invalid private export identity");
        }
        return operation;
    }

    private static String expectedMime(ExportUploadV1 request) {
        if (request.specification() != null) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        return "pdf".equals(request.projection().formato())
                ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    public static final class UploadUncertainException extends IllegalStateException {
        public UploadUncertainException(String message) {
            super(message);
        }

        public UploadUncertainException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
