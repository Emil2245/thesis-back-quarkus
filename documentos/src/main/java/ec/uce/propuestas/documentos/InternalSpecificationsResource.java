package ec.uce.propuestas.documentos;

import ec.uce.propuestas.documentos.contract.SpecificationProjectionV1;
import ec.uce.propuestas.documentos.render.SpecificationsDocxRenderer;
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

/** DOCX-specific boundary: no entities, database access, user bearer or business calculation. */
@Path("/internal/v1/render/specifications")
@Consumes(MediaType.APPLICATION_JSON)
@Produces("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
@RolesAllowed("CORE_SERVICE")
public class InternalSpecificationsResource {
    @Inject
    JsonWebToken serviceIdentity;

    @POST
    @io.smallrye.common.annotation.Blocking
    public Response render(SpecificationProjectionV1 projection) {
        long issued = serviceIdentity.getIssuedAtTime();
        long expires = serviceIdentity.getExpirationTime();
        if (issued <= 0 || expires <= issued || expires - issued > 60) {
            throw new NotAuthorizedException("Bearer");
        }
        if (projection == null || projection.schemaVersion() != SpecificationProjectionV1.VERSION) {
            throw new BadRequestException("Unsupported technical specification version");
        }
        return Response.ok(
                        SpecificationsDocxRenderer.renderizar(projection),
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .build();
    }
}
