package ec.uce.propuestas.plantilla.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Plan 04 (P-26) — Body de {@code POST /apus/{id}/guardar-plantilla}.
 * El sistema fija {@code tipo=PERSONAL}, {@code usuarioId} (del caller) y el
 * snapshot a partir del APU origen.
 */
public record PlantillaApuCrearRequest(@NotBlank @Size(max = 200) String nombre, @Size(max = 500) String descripcionRubro) {}