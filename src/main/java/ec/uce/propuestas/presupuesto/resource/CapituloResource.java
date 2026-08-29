package ec.uce.propuestas.presupuesto.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.presupuesto.dto.CapituloCrearRequest;
import ec.uce.propuestas.presupuesto.dto.CapituloEditarRequest;
import ec.uce.propuestas.presupuesto.dto.CapituloMoverRequest;
import ec.uce.propuestas.presupuesto.dto.PresupuestoResponse;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.service.CapituloService;
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

@Path("/presupuestos/{presupuestoId}/capitulos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class CapituloResource {

    @Inject
    CapituloService capituloService;

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

    @POST
    public Response crear(@PathParam("presupuestoId") Long presupuestoId, @Valid CapituloCrearRequest req) {
        validarAcceso(presupuestoId);
        PresupuestoResponse response = capituloService.crear(presupuestoId, req);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @PUT
    @Path("/{capituloId}")
    public PresupuestoResponse editar(
            @PathParam("presupuestoId") Long presupuestoId,
            @PathParam("capituloId") Long capituloId,
            @Valid CapituloEditarRequest req) {
        validarAcceso(presupuestoId);
        return capituloService.editar(presupuestoId, capituloId, req);
    }

    @PATCH
    @Path("/{capituloId}/mover")
    public PresupuestoResponse mover(
            @PathParam("presupuestoId") Long presupuestoId,
            @PathParam("capituloId") Long capituloId,
            @Valid CapituloMoverRequest req) {
        validarAcceso(presupuestoId);
        return capituloService.mover(presupuestoId, capituloId, req);
    }

    @DELETE
    @Path("/{capituloId}")
    public PresupuestoResponse eliminar(
            @PathParam("presupuestoId") Long presupuestoId, @PathParam("capituloId") Long capituloId) {
        validarAcceso(presupuestoId);
        return capituloService.eliminar(presupuestoId, capituloId);
    }
}
