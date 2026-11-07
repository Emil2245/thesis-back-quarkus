package ec.uce.propuestas.documento;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.documento.exportacion.CoreServiceJwt;
import ec.uce.propuestas.documento.exportacion.DocumentoContratoMapper;
import ec.uce.propuestas.documento.exportacion.DocumentosHttpClient;
import ec.uce.propuestas.documento.exportacion.OpcionesDocumento;
import ec.uce.propuestas.documento.exportacion.SnapshotDocumento;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Fail-closed production implementation; there is no local-writer fallback. */
@ApplicationScoped
public class RemoteDocumentosRenderGateway implements DocumentosRenderGateway {
    @Inject
    ObjectMapper json;

    @Inject
    CoreServiceJwt serviceJwt;

    @ConfigProperty(name = "documentos.base-url")
    Optional<String> baseUrl;

    @ConfigProperty(name = "documentos.timeout", defaultValue = "PT20S")
    Duration timeout;

    @Override
    public byte[] render(SnapshotDocumento snapshot, String documento, OpcionesDocumento opciones) {
        var url = baseUrl.filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalStateException("Documentos base URL is not configured"));
        var projection = DocumentoContratoMapper.proyectar(snapshot, documento, opciones);
        return new DocumentosHttpClient(URI.create(url), json, serviceJwt::issueToken, timeout).render(projection);
    }
}
