package ec.uce.propuestas.apu.resource;

import ec.uce.propuestas.apu.dto.ApuDetalleCrearRequest;
import ec.uce.propuestas.apu.dto.ApuDetallePatchRequest;
import ec.uce.propuestas.apu.dto.ApuPatchRequest;
import ec.uce.propuestas.apu.dto.ApuResponse;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.service.ApuCrudService;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.proyecto.service.ProyectoService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Agregado {@code apu} (07-api-contract.md §5, P-21/P-22). Ruta
 * {@code /apus/{apuId}}. El usuario solo opera sobre sus propios recursos (RNF-05).
 */
@Path("/apus/{apuId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class ApuResource {

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
        return usuarioRepository
                .findByEmail(email)
                .map(u -> u.id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario autenticado no encontrado"));
    }

    private void validarAcceso(Long apuId) {
        Long proyectoId = apuRepository
                .proyectoDeApu(apuId)
                .orElseThrow(() -> ProblemaException.noEncontrado("APU no encontrado"));
        proyectoService.validarPropietario(usuarioId(), proyectoId);
    }

    @GET
    @Consumes(MediaType.WILDCARD)
    public ApuResponse obtener(@PathParam("apuId") Long apuId) {
        validarAcceso(apuId);
        return apuService.obtener(apuId);
    }

    @PATCH
    public ApuResponse editarCabecera(@PathParam("apuId") Long apuId, @Valid ApuPatchRequest req) {
        validarAcceso(apuId);
        return apuService.editarCabecera(apuId, req);
    }

    @DELETE
    public Response eliminar(@PathParam("apuId") Long apuId) {
        validarAcceso(apuId);
        apuService.eliminar(apuId);
        return Response.noContent().build();
    }

    @POST
    @Path("/detalles")
    public Response agregarDetalle(@PathParam("apuId") Long apuId, @Valid ApuDetalleCrearRequest req) {
        validarAcceso(apuId);
        ApuResponse actualizado = apuService.agregarDetalle(apuId, req);
        return Response.status(Response.Status.CREATED).entity(actualizado).build();
    }

    @PATCH
    @Path("/detalles/{detalleId}")
    public ApuResponse editarDetalle(
            @PathParam("apuId") Long apuId, @PathParam("detalleId") Long detalleId, @Valid ApuDetallePatchRequest req) {
        validarAcceso(apuId);
        return apuService.editarDetalle(apuId, detalleId, req);
    }

    @DELETE
    @Path("/detalles/{detalleId}")
    public ApuResponse eliminarDetalle(@PathParam("apuId") Long apuId, @PathParam("detalleId") Long detalleId) {
        validarAcceso(apuId);
        return apuService.eliminarDetalle(apuId, detalleId);
    }
}
