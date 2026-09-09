package ec.uce.propuestas.insumo.dto;

import java.util.UUID;

/**
 * P-18: dónde se usa un insumo (bloque M/N/O/P / heredado vs override).
 *
 * <p>Plan 07 — el {@code apuId} es la identidad externa UUIDv7 del APU; nunca
 * el {@code BIGINT} interno.</p>
 */
public record InsumoUsoResponse(UUID apuId, String codigo, String descripcion, String bloque, boolean override) {}
