package ec.uce.propuestas.documento.exportacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.documentos.contract.ExportUploadV1;
import ec.uce.propuestas.documentos.contract.StoredObjectV1;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Service-authenticated upload and HEAD reconciliation; no fallback to local rendering. */
@ApplicationScoped
public class RemoteExportUploadGateway {
    @Inject
    ObjectMapper json;

    @Inject
    CoreServiceJwt serviceJwt;

    @ConfigProperty(name = "documentos.base-url")
    Optional<String> baseUrl;

    @ConfigProperty(name = "documentos.timeout", defaultValue = "PT20S")
    Duration timeout;

    public StoredObjectV1 upload(ExportUploadV1 request) {
        return client().upload(request);
    }

    public StoredObjectV1 reconcile(ExportUploadV1 request) {
        return client().reconcile(request);
    }

    private DocumentosExportClient client() {
        String url = baseUrl.filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalStateException("Documentos base URL is not configured"));
        return new DocumentosExportClient(URI.create(url), json, serviceJwt::issueToken, timeout);
    }
}
