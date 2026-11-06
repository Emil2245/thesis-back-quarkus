package ec.uce.propuestas.presupuesto.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plan 023 (P-30) — read model del resumen por componente del presupuesto.
 *
 * <p>Forma JSON estable:
 * <ul>
 *   <li>{@code porComponente}: mapa {@code <SeccionTipo, String>} en orden
 *       canónico {@code EQUIPO, MANO_OBRA, MATERIAL, TRANSPORTE}
 *       (preservado por {@link LinkedHashMap} + Jackson). Cada valor es
 *       la contribución directa M/N/O/P del árbol:
 *       {@code Σ (ApuCalculado.secciones[tipo].subtotal × rubro.cantidad)}.
 *       <b>No</b> incluye CI (los componentes son la suma directa de los
 *       subtotales del APU multiplicados por la cantidad).</li>
 *   <li>{@code totalGeneral}: cadena decimal a escala 6 (P-30). Proviene
 *       del write-through {@code presupuesto.total}, NO de la suma de los
 *       componentes (los componentes son directos, sin CI; el
 *       {@code totalGeneral} ya incluye CI vía la frontera APU→Rubro del
 *       motor).</li>
 *   <li>{@code ivaReferencial}: cadena decimal a escala 6 (precisión
 *       natural {@code BigDecimal}). Se calcula como
 *       {@code totalGeneral × ParametrosProyecto.iva} (referencial, no
 *       persistido en rubro/capítulo — DM §15 fija {@code iva} como
 *       parámetro del proyecto).</li>
 *   <li>{@code totalConIva}: cadena decimal a escala 6; suma natural
 *       {@code totalGeneral + ivaReferencial} (sin redondeo intermedio).</li>
 * </ul>
 *
 * <p>El endpoint {@code GET /presupuestos/{presupuestoId}/resumen} es
 * read-only: no muta BD. La respuesta es independiente del árbol
 * {@code PresupuestoResponse} (éste expone la estructura de capítulos y
 * rubros; el resumen expone los totales agregados por sección).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResumenComponentesResponse(
        Map<String, String> porComponente, String totalGeneral, String ivaReferencial, String totalConIva) {}
