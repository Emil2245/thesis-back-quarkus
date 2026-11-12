package ec.uce.propuestas.presupuesto.dto;

import java.util.List;

/**
 * Plan 025 (P-32) — respuesta del endpoint
 * {@code GET /presupuestos/{presupuestoId}/validacion}. Tres listas planas de
 * referencias a rubros con defectos independientes y un flag
 * {@code exportable} derivado del tamaño agregado de esas listas.
 *
 * <p>Reglas de los defectos (ortogonales — un mismo rubro puede aparecer en
 * varias listas si acumula varios defectos):
 * <ul>
 *   <li>{@code itemsPuCero}: rubros con {@code rubro.precio_unitario = 0}.</li>
 *   <li>{@code itemsCantidadCero}: rubros con {@code rubro.cantidad = 0}.</li>
 *   <li>{@code itemsSinActividad}: rubros sin actividad en el cronograma del
 *       presupuesto del path. Un presupuesto sin cronograma o sin actividad
 *       para un rubro lo marca como defectuoso.</li>
 *   <li>{@code exportable}: {@code true} sólo cuando las tres listas están
 *       vacías; {@code false} en caso contrario.</li>
 * </ul>
 *
 * <p>Las tres listas son independientes y se devuelven ordenadas por
 * {@code item} ascendente (con tie-break estable por {@code id}).</p>
 */
public record ValidacionPresupuestoResponse(
        boolean exportable,
        List<RubroRefResponse> itemsPuCero,
        List<RubroRefResponse> itemsCantidadCero,
        List<RubroRefResponse> itemsSinActividad) {}
