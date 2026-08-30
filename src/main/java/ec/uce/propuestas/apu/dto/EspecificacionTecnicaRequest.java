package ec.uce.propuestas.apu.dto;

/**
 * P-45 (N04 §ESP). {@code texto} null = limpiar; cadena vacía o con contenido = persistir.
 * El backend valida el límite en bytes UTF-8 (RNF-09: 65 536 bytes), no en caracteres Java.
 */
public record EspecificacionTecnicaRequest(String texto) {}
