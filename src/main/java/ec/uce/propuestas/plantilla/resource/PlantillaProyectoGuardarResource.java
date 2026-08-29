package ec.uce.propuestas.plantilla.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.plantilla.dto.PlantillaProyectoResponse;
import ec.uce.propuestas.plantilla.service.PlantillaProyectoService;
import ec.uce.propuestas.proyecto.service.ProyectoService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Plan 06 (P-46, N04 §A8) — Endpoint {@code POST /proyectos/{proyectoId}/guardar-plantilla}.
 * Crea una plantilla PERSONAL del caller a partir del proyecto
 * {@code proyectoId} (debe pertenecerle; RNF-05 → 404 si no).
 *
 * <p>El snapshot lo construye el backend; el cliente sólo envía
 * {@code nombre} y {@code descripcion?}. La respuesta es 201 con la forma
 * canónica de {@link PlantillaProyectoResponse}.
 */
@Path("/proyectos/{proyectoId}/guardar-plantilla")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class PlantillaProyectoGuardarResource {

    @Inject
    PlantillaProyectoService plantillaProyectoService;

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

    public record GuardarPlantillaRequest(
            @NotBlank @Size(max = 200) String nombre,
            @Size(max = 2000) String descripcion) {}

    @POST
    public Response guardar(@PathParam("proyectoId") Long proyectoId, @Valid GuardarPlantillaRequest req) {
        if (req == null || req.nombre() == null || req.nombre().isBlank()) {
            throw ProblemaException.validacion("nombre es obligatorio");
        }
        // Owner-to-404 via PlantillaProyectoService → proyectoRepository.findByIdYPropietario.
        PlantillaProyectoResponse resp =
                plantillaProyectoService.guardarDesdeProyecto(proyectoId, req.nombre(), req.descripcion(), usuarioId());
        return Response.status(Response.Status.CREATED).entity(resp).build();
    }
}
