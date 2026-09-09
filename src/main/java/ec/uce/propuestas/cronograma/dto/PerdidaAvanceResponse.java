package ec.uce.propuestas.cronograma.dto;

import java.util.UUID;

/**
 * Plan 028 — entrada determinista de la lista de pérdidas del 409
 * {@code configuracion-cronograma-requiere-confirmacion}: identifica la
 * actividad (UUIDv7), el período 1-based y el valor exacto que se perdería si
 * la reducción se confirma.
 */
public record PerdidaAvanceResponse(UUID actividadId, int periodo, String valor) {}
