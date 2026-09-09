package ec.uce.propuestas.cronograma.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Plan 030 (P-35/P-36) — bloque Gantt dentro de la respuesta canónica
 * {@link CronogramaVistasResponse}. Define exactamente la forma del canon
 * 07-api-contract §7 Apéndice B:
 *
 * <pre>
 * "gantt": { "cronograma": CronogramaResponse,
 *            "capitulos": [ CapituloCronogramaResponse ] }
 * </pre>
 *
 * <p>El frontend consume {@code gantt.cronograma} para acceder a la
 * configuración, marcadores stale, totales y todas las actividades planas
 * (igual que {@code GET /presupuestos/{id}/cronograma}); y
 * {@code gantt.capitulos} para navegar la jerarquía recursiva
 * capítulo→rubro→actividad y emitir los comandos semánticos
 * {@code MOVER_SEGMENTO} / {@code REDIMENSIONAR_SEGMENTO} de Plan 029.</p>
 *
 * <p>El array {@code capitulos} es recursivo ({@code subcapitulos[]} +
 * {@code rubros[]}); los capítulos son nodos estructurales y nunca llevan
 * una actividad adjunta — la actividad sólo cuelga del rubro (TC-P35-01).</p>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record GanttBloqueResponse(CronogramaResponse cronograma, List<CapituloCronogramaResponse> capitulos) {}
