package ec.uce.propuestas.cronograma.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.cronograma.dto.CronogramaCrearRequest;
import ec.uce.propuestas.cronograma.dto.CronogramaResponse;
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
import jakarta.ws.rs.core.Response;

@Path("/presupuestos/{presupuestoId}/cronograma")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class PresupuestoCronogramaResource {

    @Inject
    CronogramaService cronogramaService;

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

    private void validarAcceso(Long presupuestoId) {
        Presupuesto p = presupuestoService.validar(presupuestoId);
        proyectoService.validarPropietario(usuarioId(), p.proyectoId);
    }

    @GET
    @Consumes(MediaType.WILDCARD)
    public CronogramaResponse obtener(@PathParam("presupuestoId") Long presupuestoId) {
        validarAcceso(presupuestoId);
        return cronogramaService.obtener(presupuestoId);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response crear(@PathParam("presupuestoId") Long presupuestoId, @Valid CronogramaCrearRequest req) {
        validarAcceso(presupuestoId);
        CronogramaResponse resp = cronogramaService.crear(presupuestoId, req);
        return Response.status(Response.Status.CREATED).entity(resp).build();
    }
}
