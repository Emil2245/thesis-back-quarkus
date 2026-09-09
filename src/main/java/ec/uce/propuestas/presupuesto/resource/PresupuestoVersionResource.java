package ec.uce.propuestas.presupuesto.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.presupuesto.dto.PresupuestoVersionCrearRequest;
import ec.uce.propuestas.presupuesto.dto.PresupuestoVersionResponse;
import ec.uce.propuestas.presupuesto.service.PresupuestoService;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.service.ProyectoService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

@Path("/proyectos/{proyectoId}/presupuestos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class PresupuestoVersionResource {

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

    private Long validarAcceso(String proyectoPublicId) {
        UUID publicId = UuidV7.parse(proyectoPublicId);
        Proyecto proyecto = proyectoService.validarPropietario(usuarioId(), publicId);
        return proyecto.id;
    }

    @GET
    @Consumes(MediaType.WILDCARD)
    public List<PresupuestoVersionResponse> listar(@PathParam("proyectoId") String proyectoId) {
        Long id = validarAcceso(proyectoId);
        return presupuestoService.listarVersiones(id);
    }

    @POST
    public Response crear(@PathParam("proyectoId") String proyectoId, @Valid PresupuestoVersionCrearRequest req) {
        Long id = validarAcceso(proyectoId);
        PresupuestoVersionResponse version = presupuestoService.crearVersion(id, req);
        return Response.status(Response.Status.CREATED).entity(version).build();
    }
}
