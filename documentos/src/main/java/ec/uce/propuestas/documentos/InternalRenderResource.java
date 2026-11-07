package ec.uce.propuestas.documentos;

import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

/** Internal service boundary. Core owns authorization, preflight and capture; only render values arrive here. */
@Path("/internal/v1/render")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("CORE_SERVICE")
public class InternalRenderResource {
    @Inject
    JsonWebToken serviceIdentity;

    @POST
    public Response render(DocumentProjectionV1 request) {
        long issued = serviceIdentity.getIssuedAtTime();
        long expires = serviceIdentity.getExpirationTime();
        if (issued <= 0 || expires <= issued || expires - issued > 60) {
            throw new NotAuthorizedException("Bearer");
        }
        if (request == null || request.schemaVersion() != DocumentProjectionV1.VERSION) {
            throw new BadRequestException("Unsupported document contract version");
        }
        boolean validFormat = ("presupuesto".equals(request.documento()) || "apus".equals(request.documento()))
                && ("xlsx".equals(request.formato()) || "pdf".equals(request.formato()));
        if (!validFormat
                || request.snapshot() == null
                || request.snapshot().presupuestoId() == null
                || request.snapshot().proyecto() == null
                || request.snapshot().proyecto().id() == null
                || request.snapshot().parametros() == null
                || request.snapshot().display() == null
                || request.snapshot().total() == null) {
            throw new BadRequestException("Invalid document projection");
        }
        // Fail closed until the Documentos writers are migrated in T3.
        return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
    }
}
