package ec.uce.propuestas.cronograma.resource;

import com.fasterxml.jackson.databind.JsonNode;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.cronograma.dto.ActividadProgramarRequest;
import ec.uce.propuestas.cronograma.dto.CronogramaResponse;
import ec.uce.propuestas.cronograma.service.AvancePatchParser;
import ec.uce.propuestas.cronograma.service.CronogramaService;
import ec.uce.propuestas.usuario.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

/**
 * Plan 029 (P-34) — comandos de programación y avance en
 * {@code PATCH /cronogramas/{cronogramaId}/actividades/{actividadId}}.
 *
 * <p>El discriminador {@code operacion} selecciona entre las cuatro
 * operaciones congeladas por Plan 026:
 * <ul>
 *   <li>{@code REEMPLAZAR_AVANCES} — mapa completo, atómico, validado.</li>
 *   <li>{@code DISTRIBUIR_UNIFORME} — distribuye el peso entre los
 *       períodos listados.</li>
 *   <li>{@code MOVER_SEGMENTO} — desplaza un segmento máximo actual.</li>
 *   <li>{@code REDIMENSIONAR_SEGMENTO} — redimensiona conservando la suma
 *       del segmento fuente.</li>
 * </ul>
 *
 * <p>El path param {@code cronogramaId} y {@code actividadId} son UUIDv7;
 * una actividad de otro cronograma — aunque sea del mismo usuario — devuelve
 * 404 {@code no-encontrado} (no se filtra por BIGINT). El body se recibe como
 * {@link JsonNode} para rechazar propiedades desconocidas antes de construir
 * el DTO discriminado; la lógica de validación vive en
 * {@link AvancePatchParser}.</p>
 */
@Path("/cronogramas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"USUARIO", "SUPER_ADMIN"})
public class ActividadProgramarResource {

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

    @PATCH
    @Path("/{cronogramaId}/actividades/{actividadId}")
    public CronogramaResponse programar(
            @PathParam("cronogramaId") String cronogramaId,
            @PathParam("actividadId") String actividadId,
            JsonNode body) {
        UUID cronogramaPublicId = UuidV7.parse(cronogramaId);
        UUID actividadPublicId = UuidV7.parse(actividadId);
        int numeroPeriodos = cronogramaService.numeroPeriodosDe(cronogramaPublicId, usuarioId());
        ActividadProgramarRequest operacion = AvancePatchParser.parsear(body, numeroPeriodos);
        return cronogramaService.programarActividad(cronogramaPublicId, actividadPublicId, operacion, usuarioId());
    }
}
