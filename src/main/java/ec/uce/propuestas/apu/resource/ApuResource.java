package ec.uce.propuestas.apu.resource;

import ec.uce.propuestas.apu.dto.ApuCalculoResponse;
import ec.uce.propuestas.apu.dto.ApuDetalleCrearRequest;
import ec.uce.propuestas.apu.dto.ApuDetallePatchRequest;
import ec.uce.propuestas.apu.dto.ApuDuplicarRequest;
import ec.uce.propuestas.apu.dto.ApuPatchRequest;
import ec.uce.propuestas.apu.dto.ApuResponse;
import ec.uce.propuestas.apu.dto.EspecificacionTecnicaRequest;
import ec.uce.propuestas.apu.dto.EspecificacionTecnicaResponse;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.service.ApuCalculoService;
import ec.uce.propuestas.apu.service.ApuCrudService;
import ec.uce.propuestas.apu.service.ApuDuplicarService;
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
import java.math.BigDecimal;

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
    ApuDuplicarService duplicarService;

    @Inject
    ApuCalculoService calculoService;

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

    @PATCH
    @Path("/porcentaje-indirecto")
    public ApuResponse actualizarPorcentajeIndirecto(@PathParam("apuId") Long apuId, BigDecimal valor) {
        validarAcceso(apuId);
        return apuService.actualizarPorcentajeIndirecto(apuId, valor);
    }

    @PATCH
    @Path("/porcentaje-descuento")
    public ApuResponse actualizarPorcentajeDescuento(@PathParam("apuId") Long apuId, BigDecimal valor) {
        validarAcceso(apuId);
        return apuService.actualizarPorcentajeDescuento(apuId, valor);
    }

    /** P-45 (N04 §ESP). GET retorna la forma JSON estable del ET del APU. */
    @GET
    @Path("/especificacion-tecnica")
    @Consumes(MediaType.WILDCARD)
    public EspecificacionTecnicaResponse obtenerEspecificacionTecnica(@PathParam("apuId") Long apuId) {
        validarAcceso(apuId);
        return apuService.obtenerEspecificacionTecnica(apuId);
    }

    /** P-45 (N04 §ESP). PUT persiste el ET. {@code texto} null o vacío = limpiar. */
    @PUT
    @Path("/especificacion-tecnica")
    public ApuResponse guardarEspecificacionTecnica(@PathParam("apuId") Long apuId, EspecificacionTecnicaRequest req) {
        validarAcceso(apuId);
        return apuService.guardarEspecificacionTecnica(apuId, req == null ? null : req.texto());
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

    /** POST /apus/{apuId}/duplicar (dossier §B.7 opción a). Body opcional: {@code copiarET} (default false). */
    @POST
    @Path("/duplicar")
    public Response duplicar(@PathParam("apuId") Long apuId, ApuDuplicarRequest req) {
        validarAcceso(apuId);
        Boolean copiarET = req == null ? null : req.copiarET();
        ApuResponse copia = duplicarService.duplicar(apuId, copiarET);
        return Response.status(Response.Status.CREATED).entity(copia).build();
    }

    /** P-27 (dossier §B.8). Desglose de cálculo del APU (solo proyecta, no recalcula). */
    @GET
    @Path("/calculo")
    @Consumes(MediaType.WILDCARD)
    public ApuCalculoResponse calculo(@PathParam("apuId") Long apuId) {
        validarAcceso(apuId);
        ec.uce.propuestas.apu.entity.Apu apu = apuRepository.findById(apuId);
        if (apu == null) throw ProblemaException.noEncontrado("APU no encontrado");
        return calculoService.proyectar(apu);
    }
}
