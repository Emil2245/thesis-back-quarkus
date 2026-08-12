package ec.uce.propuestas.proyecto.dto;

import ec.uce.propuestas.proyecto.entity.EstadoProyecto;
import ec.uce.propuestas.proyecto.entity.PlazoUnidad;
import java.time.Instant;
import java.time.LocalDate;

public record ProyectoResponse(
        Long id,
        String nombreProyecto,
        String codigo,
        String descripcion,
        Short anio,
        LocalDate fechaInicio,
        Short plazoEjecucion,
        PlazoUnidad plazoUnidad,
        EstadoProyecto estado,
        String direccionInstitucional,
        String subdireccionInstitucional,
        boolean tieneLogo,
        Instant updatedAt) {}
