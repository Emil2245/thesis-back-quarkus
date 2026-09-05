package ec.uce.propuestas.cronograma.dto;

import java.util.List;

/**
 * Plan 028 — payload local del 409 de configuración. Conserva la forma
 * {@code {codigo, mensaje}} del {@code ErrorPayload} común y añade únicamente
 * la lista determinista {@code perdidas} exigida por el canon (D-10). No se
 * modifica el contrato de error compartido: este payload vive en el módulo
 * cronograma y solo lo usa el 409 enumerado.
 */
public record CronogramaConflictoPayload(String codigo, String mensaje, List<PerdidaAvanceResponse> perdidas) {}
