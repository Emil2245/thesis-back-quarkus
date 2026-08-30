package ec.uce.propuestas.documento.resource;

import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.documento.service.ExportApuService;
import ec.uce.propuestas.documento.service.ExportCronogramaService;
import ec.uce.propuestas.documento.service.ExportEspecificacionesService;
import ec.uce.propuestas.documento.service.ExportPresupuestoService;
import ec.uce.propuestas.presupuesto.dto.ValidacionPresupuestoResponse;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.service.PresupuestoService;
import ec.uce.propuestas.proyecto.service.ProyectoService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/documentos")
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class DocumentoResource {

    @Inject
    ApuRepository apuRepository;

    @Inject
    ExportApuService exportApuService;

    @Inject
    ExportPresupuestoService exportPresupuestoService;

    @Inject
    ExportCronogramaService exportCronogramaService;

    @Inject
    ExportEspecificacionesService exportEspecificacionesService;

    @Inject
    PresupuestoService presupuestoService;

    @Inject
    ProyectoService proyectoService;

    @Inject
    SecurityIdentity identity;

    @Inject
    UsuarioRepository usuarioRepository;

    @ConfigProperty(name = "app.display.precision-dinero", defaultValue = "2")
    int precisionDinero;

    private Long usuarioId() {
        String email = identity.getPrincipal().getName();
        return usuarioRepository
                .findByEmail(email)
                .map(u -> u.id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario autenticado no encontrado"));
    }

    private void validarAccesoPresupuesto(Long presupuestoId) {
        Presupuesto p = presupuestoService.validar(presupuestoId);
        proyectoService.validarPropietario(usuarioId(), p.proyectoId);
    }

    private void validarExportable(Long presupuestoId) {
        ValidacionPresupuestoResponse v = presupuestoService.validarIntegridad(presupuestoId);
        if (!v.exportable()) {
            throw ProblemaException.exportBloqueado(
                    "El presupuesto no cumple los requisitos para exportar. Revise rubros con precio o cantidad en cero.");
        }
    }

    @GET
    @Path("/apu/{apuId}")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response exportarApu(
            @PathParam("apuId") Long apuId, @QueryParam("formato") @DefaultValue("xlsx") String formato) {
        ec.uce.propuestas.apu.entity.Apu apu = apuRepository.findById(apuId);
        if (apu == null) throw ProblemaException.noEncontrado("APU no encontrado");
        validarAccesoPresupuesto(apu.presupuestoId);
        validarExportable(apu.presupuestoId);

        try {
            byte[] data = exportApuService.generarXlsx(apuId, precisionDinero);
            return Response.ok(data)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"APU-" + apuId + ".xlsx\"")
                    .build();
        } catch (IOException e) {
            throw new WebApplicationException("Error generando documento", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @Path("/apus/{presupuestoId}")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response exportarApus(@PathParam("presupuestoId") Long presupuestoId) {
        validarAccesoPresupuesto(presupuestoId);
        validarExportable(presupuestoId);

        try {
            byte[] data = exportApuService.generarTodosXlsx(presupuestoId, precisionDinero);
            return Response.ok(data)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"APUs-presupuesto-" + presupuestoId + ".xlsx\"")
                    .build();
        } catch (IOException e) {
            throw new WebApplicationException("Error generando documento", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @Path("/presupuesto/{presupuestoId}")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response exportarPresupuesto(@PathParam("presupuestoId") Long presupuestoId) {
        validarAccesoPresupuesto(presupuestoId);
        validarExportable(presupuestoId);

        try {
            byte[] data = exportPresupuestoService.generarXlsx(presupuestoId, precisionDinero);
            return Response.ok(data)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"Presupuesto-" + presupuestoId + ".xlsx\"")
                    .build();
        } catch (IOException e) {
            throw new WebApplicationException("Error generando documento", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @Path("/cronograma/{presupuestoId}")
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response exportarCronograma(@PathParam("presupuestoId") Long presupuestoId) {
        validarAccesoPresupuesto(presupuestoId);
        validarExportable(presupuestoId);

        try {
            byte[] data = exportCronogramaService.generarXlsx(presupuestoId, precisionDinero);
            return Response.ok(data)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"Cronograma-" + presupuestoId + ".xlsx\"")
                    .build();
        } catch (IOException e) {
            throw new WebApplicationException("Error generando documento", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @Path("/especificaciones-tecnicas/{presupuestoId}")
    @Produces("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public Response exportarEspecificaciones(
            @PathParam("presupuestoId") Long presupuestoId,
            @QueryParam("titulo1") String titulo1,
            @QueryParam("titulo2") String titulo2) {
        validarAccesoPresupuesto(presupuestoId);

        try {
            byte[] data = exportEspecificacionesService.generarDocx(presupuestoId, titulo1, titulo2);
            return Response.ok(data)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"EspecificacionesTecnicas-" + presupuestoId + ".docx\"")
                    .build();
        } catch (IOException e) {
            throw new WebApplicationException("Error generando documento", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
}
