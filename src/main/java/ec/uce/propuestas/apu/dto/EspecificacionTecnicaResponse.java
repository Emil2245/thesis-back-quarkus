package ec.uce.propuestas.apu.dto;

/**
 * P-45 (N04 §ESP). Forma estable del GET: {@code apuId} + {@code contenido} (nullable).
 * El DOCX por proyecto (cabecera + N04-bis) vive en otro slice.
 */
public record EspecificacionTecnicaResponse(Long apuId, String contenido) {}
