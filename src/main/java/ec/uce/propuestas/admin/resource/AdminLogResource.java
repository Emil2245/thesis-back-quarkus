package ec.uce.propuestas.admin.resource;

import ec.uce.propuestas.admin.dto.LogActividadResponse;
import ec.uce.propuestas.admin.entity.LogActividad;
import ec.uce.propuestas.admin.repository.LogActividadRepository;
import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.usuario.UsuarioRepository;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.List;

@Path("/admin/logs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.WILDCARD)
@RolesAllowed("SUPER_ADMIN")
public class AdminLogResource {

    @Inject
    LogActividadRepository logRepository;

    @Inject
    UsuarioRepository usuarioRepository;

    @GET
    public Page<LogActividadResponse> listar(
            @QueryParam("usuarioId") Long usuarioId,
            @QueryParam("evento") String evento,
            @QueryParam("desde") String desdeStr,
            @QueryParam("hasta") String hastaStr,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("25") int size) {
        Instant desde = desdeStr != null ? Instant.parse(desdeStr) : null;
        Instant hasta = hastaStr != null ? Instant.parse(hastaStr) : null;
        List<LogActividad> items = logRepository.buscar(usuarioId, evento, desde, hasta, page, size);
        long total = logRepository.contar(usuarioId, evento, desde, hasta);
        return Page.of(items.stream().map(this::toResponse).toList(), total, page, size);
    }

    private LogActividadResponse toResponse(LogActividad log) {
        String nombre = null;
        if (log.usuarioId != null) {
            nombre = usuarioRepository
                    .findByIdOptional(log.usuarioId)
                    .map(u -> u.nombre)
                    .orElse(null);
        }
        return new LogActividadResponse(
                log.id, log.usuarioId, nombre, log.evento, log.entidad, log.entidadId, log.detalle, log.createdAt);
    }
}
