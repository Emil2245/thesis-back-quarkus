package ec.uce.propuestas.usuario.audit.resource;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.UuidV7;
import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.usuario.audit.dto.LogActividadFiltros;
import ec.uce.propuestas.usuario.audit.dto.LogActividadResponse;
import ec.uce.propuestas.usuario.audit.service.LogActividadService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.regex.Pattern;

/** Read-only SUPER_ADMIN endpoint for the append-only activity log. */
@Path("/admin/logs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("SUPER_ADMIN")
public class LogActividadResource {

    private static final Pattern EVENTO_SEGURO = Pattern.compile("^[a-z0-9._-]+$");

    @Inject
    LogActividadService service;

    @GET
    @Consumes(MediaType.WILDCARD)
    public Page<LogActividadResponse> listar(
            @QueryParam("usuarioId") String usuarioId,
            @QueryParam("evento") String evento,
            @QueryParam("desde") String desde,
            @QueryParam("hasta") String hasta,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size) {
        validarPaginacion(page, size);
        validarEvento(evento);
        UUID actorPublicId = usuarioId == null ? null : UuidV7.parse(usuarioId);
        Instant desdeInstant = parseInstant(desde);
        Instant hastaInstant = parseInstant(hasta);
        if (desdeInstant != null && hastaInstant != null && desdeInstant.isAfter(hastaInstant)) {
            throw ProblemaException.validacion("rango-fechas-invalido");
        }
        return service.listar(new LogActividadFiltros(actorPublicId, evento, desdeInstant, hastaInstant, page, size));
    }

    private static void validarPaginacion(int page, int size) {
        if (page < 0) {
            throw ProblemaException.validacion("pagina-invalida");
        }
        if (size < 1 || size > 200) {
            throw ProblemaException.validacion("tamano-pagina-invalido");
        }
    }

    private static void validarEvento(String evento) {
        if (evento == null) {
            return;
        }
        if (evento.length() > 60) {
            throw ProblemaException.validacion("evento-largo");
        }
        if (!EVENTO_SEGURO.matcher(evento).matches()) {
            throw ProblemaException.validacion("evento-formato-invalido");
        }
    }

    private static Instant parseInstant(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ex) {
            throw ProblemaException.validacion("parametro-invalido");
        }
    }
}
