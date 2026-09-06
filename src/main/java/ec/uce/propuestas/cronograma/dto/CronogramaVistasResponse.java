package ec.uce.propuestas.cronograma.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Plan 030 (P-35/P-36) — respuesta canónica de
 * {@code GET /cronogramas/{id}/vistas}. Define la forma exacta del canon
 * 07-api-contract §7 Apéndice B:
 *
 * <pre>
 * CronogramaVistasResponse { "cronogramaId": "&lt;UUIDv7&gt;",
 *                            "gantt": GanttBloqueResponse,
 *                            "valorizado": ValorizadoBloqueResponse,
 *                            "curvaS": CurvaSResponse }
 * </pre>
 *
 * <p>La raíz sólo expone la identidad pública del cronograma; toda la
 * configuración, totales, marcadores stale y actividades viven dentro del
 * bloque {@code gantt.cronograma} como {@link CronogramaResponse} completo.
 * Ningún campo calculado se persiste.</p>
 *
 * <p>Decisiones de bloque:
 * <ul>
 *   <li>{@code gantt}: contiene el cronograma completo (mismas reglas que
 *       {@code GET /presupuestos/{id}/cronograma}) más la jerarquía recursiva
 *       capítulo → rubro → actividad. El Gantt editable reutiliza el
 *       {@code PATCH} semántico de Plan 029 (mover/redimensionar).</li>
 *   <li>{@code valorizado}: serie por período (con parcial/acumulado de
 *       porcentaje y dinero), la misma jerarquía recursiva para que el cliente
 *       pueda correlacionar fila↔período sin pedir otra vista, y el resumen
 *       de totales (avance final + monto total).</li>
 *   <li>{@code curvaS}: n puntos ordenados por período 1..n con la misma
 *       parcial/acumulada (mismo cálculo que valorizado; no recalcula).</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CronogramaVistasResponse(
        UUID cronogramaId, GanttBloqueResponse gantt, ValorizadoBloqueResponse valorizado, CurvaSResponse curvaS) {}
