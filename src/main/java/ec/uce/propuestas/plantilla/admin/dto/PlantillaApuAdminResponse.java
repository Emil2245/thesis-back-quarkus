package ec.uce.propuestas.plantilla.admin.dto;

import ec.uce.propuestas.plantilla.entity.PlantillaApu;
import java.time.Instant;
import java.util.UUID;

/** Respuesta administrativa de una plantilla APU de sistema. */
public record PlantillaApuAdminResponse(
        UUID id,
        String nombre,
        PlantillaApu.Tipo tipo,
        Long usuarioId,
        String descripcionRubro,
        Instant fechaCreacion) {

    public static PlantillaApuAdminResponse from(PlantillaApu plantilla) {
        return new PlantillaApuAdminResponse(
                plantilla.publicId,
                plantilla.nombre,
                plantilla.tipo,
                null,
                plantilla.descripcionRubro,
                plantilla.createdAt);
    }
}
