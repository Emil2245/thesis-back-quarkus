package ec.uce.propuestas.apu.dto;

import java.util.UUID;

/**
 * P-45 (N04 §ESP). Forma estable del GET: {@code apuId} + {@code contenido} (nullable).
 * El DOCX por proyecto (cabecera + N04-bis) vive en otro slice.
 */
public record EspecificacionTecnicaResponse(UUID apuId, String contenido) {}
