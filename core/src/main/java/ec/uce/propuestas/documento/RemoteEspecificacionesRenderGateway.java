package ec.uce.propuestas.documento;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.documento.exportacion.CoreServiceJwt;
import ec.uce.propuestas.documento.exportacion.DocumentosHttpClient;
import ec.uce.propuestas.documentos.contract.SpecificationProjectionV1;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Authenticated production transport; failures never invoke a local renderer. */
@ApplicationScoped
public class RemoteEspecificacionesRenderGateway implements EspecificacionesRenderGateway {
    @Inject
    ObjectMapper json;

    @Inject
    CoreServiceJwt serviceJwt;

    @ConfigProperty(name = "documentos.base-url")
    Optional<String> baseUrl;

    @ConfigProperty(name = "documentos.timeout", defaultValue = "PT20S")
    Duration timeout;

    @Override
    public byte[] render(SpecificationProjectionV1 projection) {
        var url = baseUrl.filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalStateException("Documentos base URL is not configured"));
        return new DocumentosHttpClient(URI.create(url), json, serviceJwt::issueToken, timeout)
                .renderSpecifications(projection);
    }
}
