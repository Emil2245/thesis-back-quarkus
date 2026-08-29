package ec.uce.propuestas.presupuesto.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.presupuesto.dto.PresupuestoResponse;
import ec.uce.propuestas.presupuesto.dto.RubroCrearRequest;
import ec.uce.propuestas.presupuesto.dto.RubroPatchRequest;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.service.PresupuestoService;
import ec.uce.propuestas.presupuesto.service.RubroService;
import ec.uce.propuestas.proyecto.service.ProyectoService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/presupuestos/{presupuestoId}/capitulos/{capituloId}/rubros")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class RubroResource {

    @Inject
    RubroService rubroService;

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
    public Response crear(
            @PathParam("presupuestoId") Long presupuestoId,
            @PathParam("capituloId") Long capituloId,
            @Valid RubroCrearRequest req) {
        validarAcceso(presupuestoId);
        PresupuestoResponse response = rubroService.crear(presupuestoId, capituloId, req);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @PATCH
    @Path("/{rubroId}")
    public PresupuestoResponse editar(
            @PathParam("presupuestoId") Long presupuestoId,
            @PathParam("capituloId") Long capituloId,
            @PathParam("rubroId") Long rubroId,
            @Valid RubroPatchRequest req) {
        validarAcceso(presupuestoId);
        return rubroService.editar(presupuestoId, capituloId, rubroId, req);
    }

    @DELETE
    @Path("/{rubroId}")
    public PresupuestoResponse eliminar(
            @PathParam("presupuestoId") Long presupuestoId,
            @PathParam("capituloId") Long capituloId,
            @PathParam("rubroId") Long rubroId) {
        validarAcceso(presupuestoId);
        return rubroService.eliminar(presupuestoId, capituloId, rubroId);
    }
}
