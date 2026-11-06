package ec.uce.propuestas.cronograma.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Plan 031 (P-37) — respuesta canónica del preflight
 * {@code GET /documentos/cronograma/{presupuestoId}/preflight?formato=xlsx|pdf|mspdi}.
 *
 * <pre>
 * CronogramaExportPreflightResponse { "exportable": true,
 *                                    "formato": "xlsx",
 *                                    "bloqueos": [ BloqueoExportResponse ],
 *                                    "warnings": [ WarningExportResponse ] }
 * </pre>
 *
 * <p>El flag {@code exportable} es coherente con {@code bloqueos.isEmpty()} —
 * siempre se calcula sobre una sola lectura consistente (Plan 031 §G4 + Plan
 * 026 §4: preflight y writer comparten la misma regla sobre la misma
 * proyección).</p>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CronogramaExportPreflightResponse(
        boolean exportable,
        String formato,
        List<BloqueoExportResponse> bloqueos,
        List<WarningExportResponse> warnings) {}
