package ec.uce.propuestas.cronograma.resource;

import com.fasterxml.jackson.databind.JsonNode;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.cronograma.dto.CronogramaResponse;
import ec.uce.propuestas.cronograma.service.CronogramaRequestParser;
import ec.uce.propuestas.cronograma.service.CronogramaService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

/**
 * Plan 028 (P-33) — lectura y alta del cronograma de una versión de
 * presupuesto: {@code GET|POST /presupuestos/{presupuestoId}/cronograma}.
 *
 * <p>El {@code presupuestoId} es UUIDv7 y se valida en la frontera con
 * {@link UuidV7#parse(String)}: malformado o no-v7 → 400 {@code validacion}
 * antes de tocar la base; inexistente o ajeno → 404 {@code no-encontrado}
 * (owner-to-404, nunca 403). Roles funcionales: {@code USUARIO} y
 * {@code SUPER_ADMIN}, sin un tercer rol.</p>
 *
 * <p>El body se recibe como {@link JsonNode} para poder rechazar propiedades
 * desconocidas (actividades, pesos o identidades del cliente) sin cambiar la
 * configuración global de Jackson; la validación vive en
 * {@link CronogramaRequestParser}.</p>
 */
@Path("/presupuestos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class PresupuestoCronogramaResource {

    @Inject
    CronogramaService cronogramaService;

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

    /** Read model del cronograma propio; sin fila → 404 (nunca 200 vacío). */
    @GET
    @Path("/{presupuestoId}/cronograma")
    @Consumes(MediaType.WILDCARD)
    public CronogramaResponse obtener(@PathParam("presupuestoId") String presupuestoId) {
        UUID publicId = UuidV7.parse(presupuestoId);
        return cronogramaService.obtener(publicId, usuarioId());
    }

    /** Alta 1:1 con autoimportación; duplicado o carrera → 409 {@code cronograma-ya-existe}. */
    @POST
    @Path("/{presupuestoId}/cronograma")
    public Response crear(@PathParam("presupuestoId") String presupuestoId, JsonNode body) {
        UUID publicId = UuidV7.parse(presupuestoId);
        var request = CronogramaRequestParser.parsearCrear(body);
        CronogramaResponse response = cronogramaService.crear(publicId, request, usuarioId());
        return Response.status(Response.Status.CREATED).entity(response).build();
    }
}
