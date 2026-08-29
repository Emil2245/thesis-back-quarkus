package ec.uce.propuestas.plantilla.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.plantilla.dto.PlantillaProyectoResponse;
import ec.uce.propuestas.plantilla.service.PlantillaProyectoService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

/**
 * Plan 06 (P-46, N04 §A8) — Resource REST para plantillas de proyecto.
 * Ruta {@code /plantillas-proyecto}. Reglas:
 *
 * <ul>
 *   <li>GET/DELETE — owner-scoped (RNF-05 → 404 si ajeno o no existe).</li>
 *   <li>Sin SISTEMA — el contrato solo expone plantillas del usuario (P-46).</li>
 * </ul>
 *
 * <p>El guardado es {@code POST /proyectos/{proyectoId}/guardar-plantilla} (en
 * {@link PlantillaProyectoGuardarResource}) — el snapshot lo construye el
 * backend; no se acepta snapshot JSON del cliente.
 */
@Path("/plantillas-proyecto")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class PlantillaProyectoResource {

    @Inject
    PlantillaProyectoService plantillaProyectoService;

    @Inject
    SecurityIdentity identity;

    @Inject
    UsuarioRepository usuarioRepository;

    private Long usuarioId() {
        String email = identity.getPrincipal().getName();
        return usuarioRepository
                .findByEmail(email)
                .map(u -> u.id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario autenticado no encontrado"));
    }

    /** GET /plantillas-proyecto — plantillas PERSONALES del caller. */
    @GET
    @Consumes(MediaType.WILDCARD)
    public List<PlantillaProyectoResponse> listar() {
        return plantillaProyectoService.listar(usuarioId());
    }

    /** GET /plantillas-proyecto/{id} — detalle owner-scoped. UUIDv7 mal formado → 400. */
    @GET
    @Path("/{id}")
    @Consumes(MediaType.WILDCARD)
    public PlantillaProyectoResponse detalle(@PathParam("id") String id) {
        UUID plantillaId = UuidV7.parse(id);
        return plantillaProyectoService.detalle(plantillaId, usuarioId());
    }

    /** DELETE /plantillas-proyecto/{id} — owner-scoped; 404 si ajeno. */
    @DELETE
    @Path("/{id}")
    public Response eliminar(@PathParam("id") String id) {
        UUID plantillaId = UuidV7.parse(id);
        plantillaProyectoService.eliminar(plantillaId, usuarioId());
        return Response.noContent().build();
    }
}
