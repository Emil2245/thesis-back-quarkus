package ec.uce.propuestas.presupuesto.dto;

import java.util.UUID;

/**
 * Plan 025 (P-32) — referencia reducida de un rubro para los listados de
 * defectos de validación. Forma estable de exactamente cuatro campos:
 * {@code id} (UUIDv7), {@code item}, {@code codigo} y {@code descripcion}. No
 * expone el {@code BIGINT} interno, ni los campos monetarios/unidad, ni el
 * FK {@code apuId}; la validación vive en listas top-level, no en el read
 * model del rubro (Plan 021 / P-29), por lo que este DTO no añade
 * {@code alertas} a {@link RubroResponse}.
 */
public record RubroRefResponse(UUID id, String item, String codigo, String descripcion) {}
