package ec.uce.propuestas.usuario.audit.dto;

import ec.uce.propuestas.usuario.audit.entity.LogActividad;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Stable public activity-log shape; legacy BIGINT entity identity is intentionally absent. */
public record LogActividadResponse(
        UUID id,
        UUID usuarioId,
        String usuarioNombre,
        String evento,
        String entidad,
        UUID entidadId,
        Map<String, Object> detalle,
        Instant fecha) {

    public static LogActividadResponse from(LogActividad log, Map<String, Object> detalle) {
        UUID actorPublicId = log.usuario == null ? null : log.usuario.publicId;
        String actorNombre = log.usuario == null ? null : log.usuario.nombre;
        return new LogActividadResponse(
                log.publicId,
                actorPublicId,
                actorNombre,
                log.evento,
                log.entidad,
                log.entidadPublicId,
                Collections.unmodifiableMap(new LinkedHashMap<>(detalle)),
                log.createdAt);
    }
}
