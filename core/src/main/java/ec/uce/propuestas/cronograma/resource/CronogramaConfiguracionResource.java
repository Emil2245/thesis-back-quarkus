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
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

/**
 * Plan 028 (P-33) — reemplazo de la configuración del cronograma:
 * {@code PUT /cronogramas/{cronogramaId}/configuracion}.
 *
 * <p>El recurso se separa por cohesión de raíz: el path cuelga de
 * {@code /cronogramas}, no de {@code /presupuestos}. La resolución atraviesa
 * {@code Cronograma → Presupuesto → Proyecto → caller}: UUID no-v7 → 400,
 * inexistente o ajeno → 404. El mismo body repetido es idempotente; la
 * política de pérdida/confirmación vive en el servicio.</p>
 */
@Path("/cronogramas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class CronogramaConfiguracionResource {

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

    @PUT
    @Path("/{cronogramaId}/configuracion")
    public CronogramaResponse configurar(@PathParam("cronogramaId") String cronogramaId, JsonNode body) {
        UUID publicId = UuidV7.parse(cronogramaId);
        var request = CronogramaRequestParser.parsearConfigurar(body);
        return cronogramaService.configurar(publicId, request, usuarioId());
    }
}
