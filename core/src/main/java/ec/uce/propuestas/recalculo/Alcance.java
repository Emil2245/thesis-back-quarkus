package ec.uce.propuestas.recalculo;

/**
 * Seam del módulo {@code recalculo}. Modela «qué cambió» en una mutación; el
 * {@link RecalculoService} decide qué recalcular y cómo propagar.
 *
 * <p>Los recursos REST declaran el Alcance que mutaron; nunca deciden qué
 * recalcular. Esto cumple la regla de disciplina de costuras del módulo
 * profundo (08-codebase-design.md §3).
 *
 * <p>El modelo es exactamente {@link Version} ∨ {@link Apu} ∨ {@link Insumo} —
 * no se introducen casos {@code Capitulo} ni {@code Rubro} (la propagación
 * grano fino del agregado {@code Presupuesto → Capitulo → Rubro} se sirve
 * recorriendo el árbol por alcance de versión completa). Sealed interface
 * para que el switch exhaustivo del orquestador cubra los 3 casos canónicos
 * sin más.
 */
public sealed interface Alcance permits Alcance.Version, Alcance.Apu, Alcance.Insumo {

    /** Recalcular una versión entera (capítulos raíz + totales). */
    record Version(Long presupuestoId) implements Alcance {}

    /**
     * Recalcular un APU: actualiza su costo_total y propaga a su rubro
     * (si está vinculado) y ascendentes.
     */
    record Apu(Long apuId) implements Alcance {}

    /**
     * Recalcular un insumo: propaga al APU que hereda el precio mutado
     * (override NULL) y, vía APU, a su rubro y ascendentes. Los detalles
     * con override explícito no se modifican (N04 §A1 FORMA 2).
     */
    record Insumo(Long insumoId) implements Alcance {}
}
