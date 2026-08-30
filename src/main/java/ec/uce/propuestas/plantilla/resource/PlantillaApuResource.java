package ec.uce.propuestas.plantilla.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.plantilla.dto.PlantillaApuDetalleResponse;
import ec.uce.propuestas.plantilla.dto.PlantillaApuEditarRequest;
import ec.uce.propuestas.plantilla.dto.PlantillaApuResumenResponse;
import ec.uce.propuestas.plantilla.service.PlantillaApuService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

/**
 * Plan 04 (P-26) — Resource REST para plantillas APU. Ruta
 * {@code /plantillas-apu}. Reglas (Plan 04 §3, §7):
 *
 * <ul>
 *   <li>Listar/detalle: SISTEMA visible a todos los autenticados; PERSONAL
 *       sólo del dueño.</li>
 *   <li>PUT/DELETE: SISTEMA → 404 (read-only para usuarios); PERSONAL ajena
 *       → 404 (RNF-05); PERSONAL propia → 200/204.</li>
 * </ul>
 */
@Path("/plantillas-apu")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class PlantillaApuResource {

    @Inject
    PlantillaApuService plantillaApuService;

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
     * GET /plantillas-apu?q=&tipo= — lista SISTEMA + propias. El filtro
     * {@code q} aplica a nombre/descripcionRubro. El filtro {@code tipo}
     * es opcional (SISTEMA | PERSONAL).
     */
    @GET
    @Consumes(MediaType.WILDCARD)
    public List<PlantillaApuResumenResponse> listar(@QueryParam("q") String q, @QueryParam("tipo") String tipo) {
        Long caller = usuarioId();
        List<PlantillaApuResumenResponse> todas = plantillaApuService.listar(caller, q);
        if (tipo == null || tipo.isBlank()) {
            return todas;
        }
        ec.uce.propuestas.plantilla.entity.PlantillaApu.Tipo tipoEnum;
        try {
            tipoEnum = ec.uce.propuestas.plantilla.entity.PlantillaApu.Tipo.valueOf(tipo.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ProblemaException.validacion("tipo debe ser SISTEMA o PERSONAL");
        }
        return todas.stream().filter(p -> p.tipo() == tipoEnum).toList();
    }

    @GET
    @Path("/{id}")
    @Consumes(MediaType.WILDCARD)
    public PlantillaApuDetalleResponse detalle(@PathParam("id") String id) {
        UUID plantillaId = UuidV7.parse(id);
        return plantillaApuService.detalle(plantillaId, usuarioId());
    }

    @PUT
    @Path("/{id}")
    public PlantillaApuResumenResponse editar(@PathParam("id") String id, @Valid PlantillaApuEditarRequest req) {
        UUID plantillaId = UuidV7.parse(id);
        return plantillaApuService.editar(plantillaId, req, usuarioId());
    }

    @DELETE
    @Path("/{id}")
    public Response eliminar(@PathParam("id") String id) {
        UUID plantillaId = UuidV7.parse(id);
        plantillaApuService.eliminar(plantillaId, usuarioId());
        return Response.noContent().build();
    }
}
