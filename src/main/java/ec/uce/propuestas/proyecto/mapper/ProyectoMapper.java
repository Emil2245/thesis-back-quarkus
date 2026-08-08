package ec.uce.propuestas.proyecto.mapper;

import ec.uce.propuestas.proyecto.dto.ProyectoResponse;
import ec.uce.propuestas.proyecto.entity.Proyecto;

public final class ProyectoMapper {

    private ProyectoMapper() {
    }

    public static ProyectoResponse toResponse(Proyecto p) {
        return new ProyectoResponse(
                p.id, p.nombreProyecto, p.codigo, p.descripcion, p.anio, p.fechaInicio,
                p.plazoEjecucion, p.plazoUnidad, p.estado, p.direccionInstitucional,
                p.subdireccionInstitucional, p.logo != null && p.logo.length > 0, p.updatedAt);
    }
}