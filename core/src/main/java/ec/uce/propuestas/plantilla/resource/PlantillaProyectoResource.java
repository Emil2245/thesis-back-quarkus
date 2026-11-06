package ec.uce.propuestas.plantilla.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.plantilla.dto.PlantillaProyectoResponse;
import ec.uce.propuestas.plantilla.entity.PlantillaApu;
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
 *   <li>GET — SISTEMA + PERSONALES propias (Plan 044); PERSONAL ajena → 404 (RNF-05).</li>
 *   <li>DELETE — solo PERSONAL propia; SISTEMA o ajena → 404. Las SISTEMA se
 *       gestionan en {@code /admin/plantillas-proyecto}.</li>
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

    /**
     * GET /plantillas-proyecto?tipo= — Plan 044: SISTEMA + PERSONALES del
     * caller. {@code tipo} opcional (SISTEMA | PERSONAL), como en
     * {@code GET /plantillas-apu}.
     */
    @GET
    @Consumes(MediaType.WILDCARD)
    public List<PlantillaProyectoResponse> listar(@QueryParam("tipo") String tipo) {
        PlantillaApu.Tipo filtro = null;
        if (tipo != null && !tipo.isBlank()) {
            try {
                filtro = PlantillaApu.Tipo.valueOf(tipo.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw ProblemaException.validacion("tipo debe ser SISTEMA o PERSONAL");
            }
        }
        return plantillaProyectoService.listar(usuarioId(), filtro);
    }

    /** GET /plantillas-proyecto/{id} — SISTEMA o propia. UUIDv7 mal formado → 400. */
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
