package ec.uce.propuestas.cronograma.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Plan 030 (P-35/P-36) — fila por período dentro del bloque
 * {@link ValorizadoBloqueResponse}. Forma canónica 07-api-contract §7
 * Apéndice B:
 *
 * <pre>
 * PeriodoValorizadoResponse { "periodo": 1,
 *                             "porcentajeParcial": "8.3000",
 *                             "porcentajeAcumulado": "8.3000",
 *                             "montoParcial": "830.000000",
 *                             "montoAcumulado": "830.000000" }
 * </pre>
 *
 * <p>La lista contiene exactamente {@code n} elementos (1..n) en orden
 * numérico ascendente; {@code porcentajeAcumulado} es Σ de los parciales
 * 1..t (no se repite el parcial — contraviene el error visual del PDF
 * aceptado). El acumulado es monótono no decreciente cuando los avances son
 * no negativos.</p>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PeriodoValorizadoResponse(
        int periodo,
        String porcentajeParcial,
        String porcentajeAcumulado,
        String montoParcial,
        String montoAcumulado) {}
