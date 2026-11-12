package ec.uce.propuestas.cronograma.dto;

/**
 * Plan 028 (P-33) — body de {@code PUT /cronogramas/{cronogramaId}/configuracion}.
 *
 * <p>{@code confirmarPerdida} es un comando (no se persiste) y solo tiene
 * sentido en el reintento de una reconfiguración que perdería datos o que
 * cambia la unidad con mapas no vacíos. Su ausencia NUNCA se interpreta como
 * consentimiento (D-10).</p>
 */
public record CronogramaConfigurarRequest(String unidadTiempo, int numeroPeriodos, boolean confirmarPerdida) {}
