package ec.uce.propuestas.documentos;

import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import ec.uce.propuestas.documentos.contract.ExportUploadV1;
import ec.uce.propuestas.documentos.contract.SpecificationProjectionV1;
import ec.uce.propuestas.documentos.render.SpecificationsDocxRenderer;
import ec.uce.propuestas.documentos.storage.S3PrivateObjectStore;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Objects;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;

/** Service-only write boundary; neither object keys nor private S3 URLs are exposed publicly. */
@Path("/internal/v1/export")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("CORE_SERVICE")
public class InternalExportResource {
    @Inject
    JsonWebToken serviceIdentity;

    @Inject
    S3PrivateObjectStore storage;

    @POST
    @io.smallrye.common.annotation.Blocking
    public S3PrivateObjectStore.StoredObject upload(ExportUploadV1 request) {
        var document = render(request);
        return storage.store(
                request.key(), document.bytes(), document.mimeType(), request.filename(), request.fingerprint());
    }

    /** Used only after an uncertain upload: HEAD must match re-rendered bytes and request fingerprint. */
    @POST
    @Path("reconcile")
    @io.smallrye.common.annotation.Blocking
    public S3PrivateObjectStore.StoredObject reconcile(ExportUploadV1 request) {
        var document = render(request);
        return storage.confirm(
                request.key(), document.bytes(), document.mimeType(), request.filename(), request.fingerprint());
    }

    private DocumentRenderService.Rendered render(ExportUploadV1 request) {
        long issued = serviceIdentity.getIssuedAtTime();
        long expires = serviceIdentity.getExpirationTime();
        if (issued <= 0 || expires <= issued || expires - issued > 60) {
            throw new NotAuthorizedException("Bearer");
        }
        if (request == null
                || request.schemaVersion() != ExportUploadV1.VERSION
                || request.operationId() == null
                || request.key() == null
                || request.fingerprint() == null
                || !request.fingerprint().matches("[0-9a-f]{64}")
                || request.filename() == null
                || !request.filename().matches("[A-Za-z0-9._-]{1,128}")
                || (request.projection() == null) == (request.specification() == null)) {
            throw new BadRequestException("Invalid private export request");
        }
        UUID operationId;
        try {
            operationId = UUID.fromString(request.operationId());
        } catch (IllegalArgumentException invalid) {
            throw new BadRequestException("Invalid private export operation ID");
        }
        if (operationId.version() != 7
                || !operationId.toString().equals(request.operationId())
                || !request.key().equals("exports/" + operationId)) {
            throw new BadRequestException("Invalid private export key");
        }
        if (!Objects.equals(request.operationId(), serviceIdentity.getClaim("operationId"))) {
            throw new NotAuthorizedException("Bearer");
        }
        if (request.projection() != null) {
            DocumentProjectionV1 projection = request.projection();
            String suffix = "." + projection.formato();
            if (!request.filename().endsWith(suffix)) {
                throw new BadRequestException("Invalid export filename");
            }
            return DocumentRenderService.render(projection);
        }
        SpecificationProjectionV1 specification = request.specification();
        if (specification.schemaVersion() != SpecificationProjectionV1.VERSION
                || !request.filename().endsWith(".docx")) {
            throw new BadRequestException("Invalid technical specification upload");
        }
        return new DocumentRenderService.Rendered(
                SpecificationsDocxRenderer.renderizar(specification),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }
}
