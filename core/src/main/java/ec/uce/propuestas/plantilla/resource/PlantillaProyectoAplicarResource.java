package ec.uce.propuestas.plantilla.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.plantilla.dto.ProyectoDesdePlantillaResponse;
import ec.uce.propuestas.plantilla.service.PlantillaProyectoService;
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
import java.util.UUID;

/**
 * Plan 06 (P-46, N04 §A8) — Endpoint
 * {@code POST /proyectos/desde-plantilla/{plantillaId}}. Crea un nuevo proyecto
 * del caller a partir de la plantilla estructural {@code plantillaId}. El
 * cliente sólo envía el {@code nombreProyecto}; el resto lo construye el
 * backend (cabecera + parámetros + presupuesto v1 + base PROYECTO +
 * capítulos/APUs recursivos). El cliente edita cantidades de obra y precios
 * después.
 *
 * <p>Códigos HTTP:
 * <ul>
 *   <li>{@code 201 Created} — sin advertencias (todos los insumos resueltos).</li>
 *   <li>{@code 200 OK} — con {@code advertencias[]} no vacío (códigos no
 *       resueltos en PROYECTO/CENTRAL/PERSONAL).</li>
 * </ul>
 *
 * <p>UUIDv7 mal formado → 400 {@code validacion}. Plantilla ajena o inexistente
 * → 404 {@code no-encontrado} (RNF-05).
 */
@Path("/proyectos/desde-plantilla/{plantillaId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class PlantillaProyectoAplicarResource {

    @Inject
    PlantillaProyectoService plantillaProyectoService;

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

    public record AplicarPlantillaRequest(
            @NotBlank @Size(max = 200) String nombre) {}

    @POST
    public Response aplicar(@PathParam("plantillaId") String plantillaIdStr, @Valid AplicarPlantillaRequest req) {
        UUID plantillaPublicId = UuidV7.parse(plantillaIdStr);
        if (req == null || req.nombre() == null || req.nombre().isBlank()) {
            throw ProblemaException.validacion("nombre es obligatorio");
        }
        ProyectoDesdePlantillaResponse out =
                plantillaProyectoService.aplicar(plantillaPublicId, req.nombre(), usuarioId());
        if (out.tieneAdvertencias()) {
            return Response.ok(out).build();
        }
        return Response.status(Response.Status.CREATED).entity(out).build();
    }
}
