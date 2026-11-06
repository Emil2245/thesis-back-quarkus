package ec.uce.propuestas.cronograma.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Plan 031 (P-37) — una advertencia no bloqueante del preflight del cronograma.
 * El único código actualmente en uso es {@code cronograma-desactualizado} —
 * Plan 026 §4 permite descargar y exportar aun cuando el snapshot del
 * presupuesto cambió desde la última revisión explícita.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record WarningExportResponse(String codigo, String detalle) {

    public static WarningExportResponse stale(String detalle) {
        return new WarningExportResponse("cronograma-desactualizado", detalle);
    }
}
