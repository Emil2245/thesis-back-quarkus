package ec.uce.propuestas.apu.resource;

import ec.uce.propuestas.apu.dto.ApuCrearRequest;
import ec.uce.propuestas.apu.dto.ApuResponse;
import ec.uce.propuestas.apu.dto.ApuResumenResponse;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.service.ApuCrudService;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.proyecto.service.ProyectoService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * APUs de una versión de presupuesto (07-api-contract.md §5, P-19/P-20).
 * Ruta {@code /presupuestos/{presupuestoId}/apus}.
 */
@Path("/presupuestos/{presupuestoId}/apus")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class PresupuestoApuResource {

    @Inject
    ApuCrudService apuService;
    @Inject
    ApuRepository apuRepository;
    @Inject
    ProyectoService proyectoService;
    @Inject
    SecurityIdentity identity;
    @Inject
    UsuarioRepository usuarioRepository;

    private Long usuarioId() {
        String email = identity.getPrincipal().getName();
        return usuarioRepository.findByEmail(email)
                .map(u -> u.id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario autenticado no encontrado"));
    }

    private void validarAcceso(Long presupuestoId) {
        Long proyectoId = apuRepository.proyectoDePresupuesto(presupuestoId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));
        proyectoService.validarPropietario(usuarioId(), proyectoId);
    }

    @GET
    @Consumes(MediaType.WILDCARD)
    public Page<ApuResumenResponse> listar(
            @PathParam("presupuestoId") Long presupuestoId,
            @QueryParam("q") String q,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size) {
        validarAcceso(presupuestoId);
        return apuService.listar(presupuestoId, q, page, size);
    }

    @POST
    public Response crear(@PathParam("presupuestoId") Long presupuestoId,
                          @Valid ApuCrearRequest req) {
        validarAcceso(presupuestoId);
        ApuResponse creado = apuService.crear(presupuestoId, req);
        return Response.status(Response.Status.CREATED).entity(creado).build();
    }
}