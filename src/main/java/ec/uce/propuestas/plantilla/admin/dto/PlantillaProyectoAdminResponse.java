package ec.uce.propuestas.plantilla.admin.dto;

import ec.uce.propuestas.plantilla.entity.PlantillaApu;
import ec.uce.propuestas.plantilla.entity.PlantillaProyecto;
import java.time.Instant;
import java.util.UUID;

/**
 * Plan 044 — respuesta administrativa de una plantilla de proyecto SISTEMA.
 * Sin snapshot: el detalle completo sale de {@code GET /plantillas-proyecto/{id}},
 * que ya muestra las SISTEMA a cualquier usuario autenticado.
 */
public record PlantillaProyectoAdminResponse(
        UUID id, String nombre, PlantillaApu.Tipo tipo, String descripcion, Instant fechaCreacion) {

    public static PlantillaProyectoAdminResponse from(PlantillaProyecto plantilla) {
        return new PlantillaProyectoAdminResponse(
                plantilla.publicId, plantilla.nombre, plantilla.tipo, plantilla.descripcion, plantilla.fechaCreacion);
    }
}
