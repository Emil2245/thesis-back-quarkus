package ec.uce.propuestas.plantilla.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import ec.uce.propuestas.plantilla.entity.PlantillaApu;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Plan 04 (P-26) — Detalle completo de una plantilla APU. Expone el snapshot
 * JSONB tal cual se persiste: el cliente lo interpreta como nodo opaco. Las
 * advertencias sólo aparecen en el detalle de creación / carga (no en el
 * listado), siguiendo la forma del contrato 07-api-contract.md.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlantillaApuDetalleResponse(
        UUID id,
        String nombre,
        PlantillaApu.Tipo tipo,
        String descripcionRubro,
        String unidad,
        JsonNode snapshotSecciones,
        Instant createdAt,
        Instant updatedAt,
        List<AdvertenciaPlantillaResponse> advertencias) {

    public static PlantillaApuDetalleResponse from(PlantillaApu p, JsonNode snapshot, List<AdvertenciaPlantillaResponse> advertencias) {
        return new PlantillaApuDetalleResponse(
                p.publicId,
                p.nombre,
                p.tipo,
                p.descripcionRubro,
                p.unidad,
                snapshot,
                p.createdAt,
                p.updatedAt,
                advertencias);
    }
}