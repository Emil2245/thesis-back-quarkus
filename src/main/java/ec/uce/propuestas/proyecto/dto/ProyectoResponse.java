package ec.uce.propuestas.proyecto.dto;

import ec.uce.propuestas.proyecto.entity.EstadoProyecto;
import ec.uce.propuestas.proyecto.entity.PlazoUnidad;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Plan 07 — Identidad externa inmutable UUIDv7 (columna {@code public_id}).
 * El campo {@code id} del JSON es siempre un UUIDv7 (semántico, no {@code publicId}).
 */
public record ProyectoResponse(
        UUID id,
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
