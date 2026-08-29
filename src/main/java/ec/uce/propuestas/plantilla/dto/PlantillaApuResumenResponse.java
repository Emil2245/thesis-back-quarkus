package ec.uce.propuestas.plantilla.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import ec.uce.propuestas.plantilla.entity.PlantillaApu;
import java.time.Instant;
import java.util.UUID;

/**
 * Plan 04 (P-26) — Listado / detalle resumido de una plantilla APU. ID público
 * UUIDv7 (OpenSpec WU-03 — {@code plantillaId}); la identidad interna BIGINT
 * se retiene debajo de la seam.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlantillaApuResumenResponse(
        UUID id,
        String nombre,
        PlantillaApu.Tipo tipo,
        String descripcionRubro,
        String unidad,
        Instant createdAt,
        Instant updatedAt) {

    public static PlantillaApuResumenResponse from(PlantillaApu p) {
        return new PlantillaApuResumenResponse(
                p.publicId, p.nombre, p.tipo, p.descripcionRubro, p.unidad, p.createdAt, p.updatedAt);
    }
}