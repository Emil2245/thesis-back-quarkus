package ec.uce.propuestas.presupuesto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Plan 022 (P-28) — body para {@code PUT
 * /presupuestos/{presupuestoId}/capitulos/{capituloId}}. Sólo cambia la
 * descripción; {@code item} y {@code orden} se mantienen (no son editables
 * directamente — para reposicionar se usa {@code PATCH …/mover}).
 */
public record CapituloEditarRequest(@NotBlank @Size(max = 255) String descripcion) {}