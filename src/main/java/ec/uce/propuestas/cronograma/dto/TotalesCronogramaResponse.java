package ec.uce.propuestas.cronograma.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Plan 030 (P-35/P-36) — bloque de totales del cronograma valorizado.
 * Forma canónica derivada de 07-api-contract §7 Apéndice B (marcadores
 * visibles pertinentes del cronograma).
 *
 * <pre>
 * TotalesCronogramaResponse { "avanceFinalPorcentaje": "100.0000",
 *                             "montoTotalGeneral": "10000.000000",
 *                             "porcentajeCierre": "100.0000" }
 * </pre>
 *
 * <p>Estos totales consolidan la serie por período en un solo resumen.
 * {@code avanceFinalPorcentaje} debe ser {@code "100.0000"} para que el
 * estado de distribución pase a {@code COMPLETO} (Plan 026 §4). El monto
 * total general es Σ de {@code montoParcialPorPeriodo[1..n]} y reconcilia con
 * el precio total del presupuesto cuando la distribución está completa.</p>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TotalesCronogramaResponse(
        String avanceFinalPorcentaje, String montoTotalGeneral, String porcentajeCierre) {}
