package ec.uce.propuestas.plantilla.resource;

import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.plantilla.dto.PlantillaApuCrearRequest;
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
import java.util.UUID;

/**
 * Plan 04 (P-26) — Endpoint {@code POST /apus/{apuId}/guardar-plantilla}.
 * Crea una plantilla PERSONAL a partir de un APU existente del caller
 * (snapshot sin precios, preservando secciones/orden/HM).
 */
@Path("/apus/{apuId}/guardar-plantilla")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class PlantillaApuGuardarResource {

    @Inject
    PlantillaApuService plantillaApuService;

    @Inject
    ApuRepository apuRepository;

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

    @POST
    public Response guardar(@PathParam("apuId") String apuIdStr, @Valid PlantillaApuCrearRequest req) {
        UUID apuPublicId = UuidV7.parse(apuIdStr);
        Long apuInternalId = apuRepository
                .findByPublicIdAndOwnerScope(apuPublicId, usuarioId())
                .orElseThrow(() -> ProblemaException.noEncontrado("APU no encontrado"))
                .id;
        PlantillaApuResumenResponse resp = plantillaApuService.guardarDesdeApu(apuInternalId, req, usuarioId());
        return Response.status(Response.Status.CREATED).entity(resp).build();
    }
}