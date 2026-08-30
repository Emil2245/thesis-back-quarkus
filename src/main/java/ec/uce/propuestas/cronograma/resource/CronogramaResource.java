package ec.uce.propuestas.cronograma.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.cronograma.dto.ActividadAvanceRequest;
import ec.uce.propuestas.cronograma.dto.CronogramaConfigurarRequest;
import ec.uce.propuestas.cronograma.dto.CronogramaResponse;
import ec.uce.propuestas.cronograma.entity.Cronograma;
import ec.uce.propuestas.cronograma.repository.CronogramaRepository;
import ec.uce.propuestas.cronograma.service.CronogramaService;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.service.PresupuestoService;
import ec.uce.propuestas.proyecto.service.ProyectoService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/cronogramas/{cronogramaId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class CronogramaResource {

    @Inject
    CronogramaService cronogramaService;

    @Inject
    CronogramaRepository cronogramaRepository;

    @Inject
    PresupuestoService presupuestoService;

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

    private void validarAcceso(Long cronogramaId) {
        Cronograma c = cronogramaRepository
                .findByIdOptional(cronogramaId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Cronograma no encontrado"));
        Presupuesto p = presupuestoService.validar(c.presupuestoId);
        proyectoService.validarPropietario(usuarioId(), p.proyectoId);
    }

    @PUT
    public CronogramaResponse configurar(
            @PathParam("cronogramaId") Long cronogramaId, CronogramaConfigurarRequest req) {
        validarAcceso(cronogramaId);
        return cronogramaService.configurar(cronogramaId, req);
    }

    @PATCH
    @Path("/actividades/{actividadId}")
    public CronogramaResponse actualizarAvance(
            @PathParam("cronogramaId") Long cronogramaId,
            @PathParam("actividadId") Long actividadId,
            @Valid ActividadAvanceRequest req) {
        validarAcceso(cronogramaId);
        return cronogramaService.actualizarAvance(cronogramaId, actividadId, req);
    }

    @POST
    @Path("/revisado")
    @Consumes(MediaType.WILDCARD)
    public CronogramaResponse marcarRevisado(@PathParam("cronogramaId") Long cronogramaId) {
        validarAcceso(cronogramaId);
        return cronogramaService.marcarRevisado(cronogramaId);
    }
}
