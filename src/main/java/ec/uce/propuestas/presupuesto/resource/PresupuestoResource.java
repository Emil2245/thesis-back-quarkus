package ec.uce.propuestas.presupuesto.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.presupuesto.dto.*;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.service.DescuentoGlobalService;
import ec.uce.propuestas.presupuesto.service.PresupuestoService;
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

@Path("/presupuestos/{presupuestoId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class PresupuestoResource {

    @Inject
    PresupuestoService presupuestoService;

    @Inject
    DescuentoGlobalService descuentoGlobalService;

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

    private Presupuesto validarAcceso(Long presupuestoId) {
        Presupuesto p = presupuestoService.validar(presupuestoId);
        proyectoService.validarPropietario(usuarioId(), p.proyectoId);
        return p;
    }

    @GET
    @Consumes(MediaType.WILDCARD)
    public PresupuestoResponse obtener(@PathParam("presupuestoId") Long presupuestoId) {
        validarAcceso(presupuestoId);
        return presupuestoService.obtenerArbol(presupuestoId);
    }

    @POST
    @Path("/vigente")
    @Consumes(MediaType.WILDCARD)
    public PresupuestoVersionResponse marcarVigente(@PathParam("presupuestoId") Long presupuestoId) {
        validarAcceso(presupuestoId);
        return presupuestoService.marcarVigente(presupuestoId);
    }

    @DELETE
    @Consumes(MediaType.WILDCARD)
    public Response eliminar(@PathParam("presupuestoId") Long presupuestoId) {
        validarAcceso(presupuestoId);
        presupuestoService.eliminar(presupuestoId);
        return Response.noContent().build();
    }

    @GET
    @Path("/resumen")
    @Consumes(MediaType.WILDCARD)
    public ResumenComponentesResponse resumen(@PathParam("presupuestoId") Long presupuestoId) {
        validarAcceso(presupuestoId);
        return presupuestoService.obtenerResumenComponentes(presupuestoId);
    }

    @GET
    @Path("/comparar")
    @Consumes(MediaType.WILDCARD)
    public ComparacionVersionesResponse comparar(
            @PathParam("presupuestoId") Long presupuestoId, @QueryParam("con") Long conPresupuestoId) {
        if (conPresupuestoId == null) {
            throw ProblemaException.validacion("El parámetro 'con' es obligatorio para comparar");
        }
        validarAcceso(presupuestoId);
        validarAcceso(conPresupuestoId);
        return presupuestoService.compararVersiones(presupuestoId, conPresupuestoId);
    }

    @GET
    @Path("/validacion")
    @Consumes(MediaType.WILDCARD)
    public ValidacionPresupuestoResponse validacion(@PathParam("presupuestoId") Long presupuestoId) {
        validarAcceso(presupuestoId);
        return presupuestoService.validarIntegridad(presupuestoId);
    }

    @GET
    @Path("/descuento-global/preview")
    @Consumes(MediaType.WILDCARD)
    public DescuentoGlobalPreviewResponse previewDescuento(
            @PathParam("presupuestoId") Long presupuestoId, @QueryParam("porcentaje") BigDecimal porcentaje) {
        if (porcentaje == null
                || porcentaje.compareTo(BigDecimal.ZERO) < 0
                || porcentaje.compareTo(new BigDecimal("0.5000")) > 0) {
            throw ProblemaException.validacion("El porcentaje debe estar entre 0 y 0.5000");
        }
        validarAcceso(presupuestoId);
        return descuentoGlobalService.preview(presupuestoId, porcentaje);
    }

    @POST
    @Path("/descuento-global")
    public PresupuestoResponse aplicarDescuento(
            @PathParam("presupuestoId") Long presupuestoId, @Valid DescuentoGlobalRequest req) {
        validarAcceso(presupuestoId);
        return descuentoGlobalService.aplicar(presupuestoId, req);
    }
}
