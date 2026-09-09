package ec.uce.propuestas.presupuesto.dto;

/**
 * Plan 024 (P-31) — fila del desglose por capítulo raíz dentro de la
 * comparación. Sólo expone {@code item}, {@code descripcion} y {@code total} (a
 * escala 6) — no incluye jerarquía descendiente. La elección descarta el
 * sub-árbol completo para mantener el payload enfocado en totales agregados de
 * comparación; los hijos ya están reflejados en el {@code total} del raíz vía
 * el write-through de {@code RecalculoService}.
 */
public record CapituloRaizComparacion(String item, String descripcion, String total) {}
