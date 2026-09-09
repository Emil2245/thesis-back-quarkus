package ec.uce.propuestas.presupuesto.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.presupuesto.dto.PresupuestoVersionResponse;
import ec.uce.propuestas.presupuesto.service.VersionadoService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

/** Plan 024 — selección de versión vigente y eliminación protegida. */
@Path("/presupuestos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class PresupuestoVigenciaResource {

    @Inject
    VersionadoService versionadoService;

    @Inject
    SecurityIdentity identity;

    @Inject
    UsuarioRepository usuarioRepository;

    @POST
    @Path("/{presupuestoId}/vigente")
    @Consumes(MediaType.WILDCARD)
    public PresupuestoVersionResponse marcarVigente(@PathParam("presupuestoId") String presupuestoId) {
        UUID presupuestoPublicId = UuidV7.parse(presupuestoId);
        return versionadoService.marcarVigente(presupuestoPublicId, usuarioId());
    }

    @DELETE
    @Path("/{presupuestoId}")
    @Consumes(MediaType.WILDCARD)
    public Response eliminar(@PathParam("presupuestoId") String presupuestoId) {
        UUID presupuestoPublicId = UuidV7.parse(presupuestoId);
        versionadoService.eliminar(presupuestoPublicId, usuarioId());
        return Response.noContent().build();
    }

    private Long usuarioId() {
        String email = identity.getPrincipal().getName();
        return usuarioRepository
                .findByEmail(email)
                .map(u -> u.id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario autenticado no encontrado"));
    }
}
