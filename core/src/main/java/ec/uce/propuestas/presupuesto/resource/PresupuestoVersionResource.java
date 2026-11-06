package ec.uce.propuestas.presupuesto.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.presupuesto.dto.PresupuestoVersionCrearRequest;
import ec.uce.propuestas.presupuesto.dto.PresupuestoVersionResponse;
import ec.uce.propuestas.presupuesto.service.VersionadoService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

/** Plan 024 — creación de versiones mediante deep copy. */
@Path("/proyectos/{proyectoId}/presupuestos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class PresupuestoVersionResource {

    @Inject
    VersionadoService versionadoService;

    @Inject
    SecurityIdentity identity;

    @Inject
    UsuarioRepository usuarioRepository;

    @POST
    public Response crear(@PathParam("proyectoId") String proyectoId, @Valid PresupuestoVersionCrearRequest req) {
        UUID proyectoPublicId = UuidV7.parse(proyectoId);
        UUID origenPublicId = UuidV7.parse(req.origenId().toString());
        PresupuestoVersionResponse body =
                versionadoService.copiarVersion(proyectoPublicId, origenPublicId, req, usuarioId());
        return Response.status(Response.Status.CREATED).entity(body).build();
    }

    private Long usuarioId() {
        String email = identity.getPrincipal().getName();
        return usuarioRepository
                .findByEmail(email)
                .map(u -> u.id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario autenticado no encontrado"));
    }
}
