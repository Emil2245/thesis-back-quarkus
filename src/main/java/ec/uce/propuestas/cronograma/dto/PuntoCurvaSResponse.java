package ec.uce.propuestas.cronograma.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Plan 030 — punto de la curva S. La curva contiene exactamente {@code n}
 * puntos ordenados por período 1..n (longitud fija igual a
 * {@code numeroPeriodos}). El acumulado de porcentajes es monótono no
 * decreciente cuando los avances son no negativos.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PuntoCurvaSResponse(
        int periodo,
        String porcentajeParcial,
        String porcentajeAcumulado,
        String montoParcial,
        String montoAcumulado) {}
