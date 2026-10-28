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
 *                             "porcentajeCierre": "100.0000",
 *                             "montoPresupuesto": "10000.000000",
 *                             "montoSinProgramar": "0.000000",
 *                             "actividadesSinAsignar": 0 }
 * </pre>
 *
 * <p>Estos totales consolidan la serie por período en un solo resumen.
 * {@code avanceFinalPorcentaje} debe ser {@code "100.0000"} para que el
 * estado de distribución pase a {@code COMPLETO} (Plan 026 §4). El monto
 * total general es Σ de {@code montoParcialPorPeriodo[1..n]} y reconcilia con
 * el precio total del presupuesto cuando la distribución está completa.</p>
 *
 * <p>Mientras haya actividades sin programar, el valorizado y la curva S suman
 * solo lo programado. Para no ocultar la brecha: {@code montoPresupuesto} es el
 * total del presupuesto, {@code montoSinProgramar} = max(0, montoPresupuesto -
 * montoTotalGeneral) y {@code actividadesSinAsignar} cuenta las actividades con
 * distribución incompleta (mapa vacío o con suma distinta de su peso; mismo criterio
 * que el estado BORRADOR/COMPLETO). El nombre se conserva por compatibilidad.</p>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TotalesCronogramaResponse(
        String avanceFinalPorcentaje,
        String montoTotalGeneral,
        String porcentajeCierre,
        String montoPresupuesto,
        String montoSinProgramar,
        int actividadesSinAsignar) {}
