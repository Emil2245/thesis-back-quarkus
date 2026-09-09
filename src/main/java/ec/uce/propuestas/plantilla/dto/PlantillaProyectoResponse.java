package ec.uce.propuestas.plantilla.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import ec.uce.propuestas.plantilla.entity.PlantillaProyecto;
import java.time.Instant;
import java.util.UUID;

/**
 * Plan 06 (P-46, N04 §A8) — Respuesta pública de una plantilla de proyecto.
 * ID UUIDv7 (WU-03), snapshot JSONB opaco, {@code descripcion} opcional del
 * autor. No expone IDs internos BIGINT ni campos sensibles.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlantillaProyectoResponse(
        UUID id, String nombre, String descripcion, JsonNode snapshotEstructura, Instant fechaCreacion) {

    public static PlantillaProyectoResponse from(PlantillaProyecto p, JsonNode snapshot) {
        return new PlantillaProyectoResponse(p.publicId, p.nombre, p.descripcion, snapshot, p.fechaCreacion);
    }
}
