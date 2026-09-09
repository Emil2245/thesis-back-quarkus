package ec.uce.propuestas.proyecto.mapper;

import ec.uce.propuestas.proyecto.dto.ProyectoResponse;
import ec.uce.propuestas.proyecto.entity.Proyecto;

public final class ProyectoMapper {

    private ProyectoMapper() {}

    /**
     * Plan 07 — el {@code id} público es el {@code publicId} UUIDv7 de la fila;
     * el {@code BIGINT} interno nunca aparece en el JSON. Mantener el nombre
     * semántico {@code id} (no {@code publicId}).
     */
    public static ProyectoResponse toResponse(Proyecto p) {
        return new ProyectoResponse(
                p.publicId,
                p.nombreProyecto,
                p.codigo,
                p.descripcion,
                p.anio,
                p.fechaInicio,
                p.plazoEjecucion,
                p.plazoUnidad,
                p.estado,
                p.direccionInstitucional,
                p.subdireccionInstitucional,
                p.logo != null && p.logo.length > 0,
                p.updatedAt);
    }
}
