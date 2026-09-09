package ec.uce.propuestas.cronograma.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import java.util.UUID;

/**
 * Plan 030 (P-35/P-36) — nodo de rubro dentro de la jerarquía recursiva
 * {@link CapituloCronogramaResponse}.
 *
 * <p>Forma canónica 07-api-contract §7 Apéndice B (extendida por Plan 030 con
 * los campos monetarios derivados, ya que la canon exige exponer el monto
 * valorizado por rubro — no por actividad — a escala 6):
 *
 * <pre>
 * RubroCronogramaResponse { "id": "&lt;UUIDv7&gt;",
 *                           "item": "1.1.1",
 *                           "codigo": "",
 *                           "descripcion": "",
 *                           "unidad": "m²",
 *                           "cantidad": "1.000000",
 *                           "precioUnitario": "2500.00",
 *                           "precioTotal": "2500.000000",
 *                           "montoPorPeriodo": { "1": "833.333333", ... },
 *                           "montoTotal": "2500.000000",
 *                           "actividad": ActividadCronogramaResponse | null }
 * </pre>
 *
 * <p>Los campos de rubro son inmutables (derivados del presupuesto; nunca se
 * duplican como columnas editables en actividad, V001 §2.13 + D-09). La
 * actividad embebida puede ser {@code null} únicamente cuando el rubro aún
 * no tiene actividad en su cronograma (presupuesto sin cronograma creado). En
 * un cronograma vigente, cada rubro de la versión tiene exactamente una
 * actividad (UNIQUE {@code actividad.rubro_id}).</p>
 *
 * <p>Los campos monetarios {@code montoPorPeriodo} y {@code montoTotal} derivan
 * del cierre por actividad (DM §16) ya computado en
 * {@code VistasCronogramaService.cerrarActividad}. La forma
 * {@link ActividadCronogramaResponse} — congelada por Plan 028/029 — no
 * expone estos campos para preservar el contrato vigente; se promueven al
 * rubro, que es el nodo canónico del valorizado jerárquico. Cuando el rubro
 * no tiene actividad, ambos campos monetarios son {@code null}.</p>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RubroCronogramaResponse(
        UUID id,
        String item,
        String codigo,
        String descripcion,
        String unidad,
        String cantidad,
        String precioUnitario,
        String precioTotal,
        Map<String, String> montoPorPeriodo,
        String montoTotal,
        ActividadCronogramaResponse actividad) {}
