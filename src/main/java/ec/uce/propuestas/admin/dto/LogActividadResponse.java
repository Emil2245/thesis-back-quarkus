package ec.uce.propuestas.admin.dto;

import java.time.Instant;
import java.util.Map;

public record LogActividadResponse(
        Long id,
        Long usuarioId,
        String usuarioNombre,
        String evento,
        String entidad,
        Long entidadId,
        Map<String, Object> detalle,
        Instant createdAt) {}
