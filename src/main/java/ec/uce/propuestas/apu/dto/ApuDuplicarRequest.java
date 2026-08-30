package ec.uce.propuestas.apu.dto;

/**
 * POST /apus/{apuId}/duplicar (dossier §B.7 opción a). {@code copiarET} controla
 * si se duplica la especificación técnica (P-45) del APU origen.
 *
 * <p>Body ausente o {@code {"copiarET": null}} → no copiar ET (default {@code false}).
 */
public record ApuDuplicarRequest(Boolean copiarET) {}
