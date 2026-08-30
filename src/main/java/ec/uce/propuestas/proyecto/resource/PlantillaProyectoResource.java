package ec.uce.propuestas.proyecto.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.proyecto.dto.PlantillaProyectoCrearRequest;
import ec.uce.propuestas.proyecto.dto.PlantillaProyectoResponse;
import ec.uce.propuestas.proyecto.service.PlantillaProyectoService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/plantillas-proyecto")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class PlantillaProyectoResource {

    @Inject
    PlantillaProyectoService plantillaService;

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

    @GET
    public List<PlantillaProyectoResponse> listar() {
        return plantillaService.listar(usuarioId());
    }

    @POST
    public Response crear(@Valid PlantillaProyectoCrearRequest req) {
        PlantillaProyectoResponse resp = plantillaService.crear(usuarioId(), req);
        return Response.status(Response.Status.CREATED).entity(resp).build();
    }

    @DELETE
    @Path("/{plantillaId}")
    public Response eliminar(@PathParam("plantillaId") Long plantillaId) {
        plantillaService.eliminar(usuarioId(), plantillaId);
        return Response.noContent().build();
    }
}
