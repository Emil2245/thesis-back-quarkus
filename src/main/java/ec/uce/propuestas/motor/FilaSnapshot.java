package ec.uce.propuestas.motor;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable snapshot of one line (fila) in an APU section.
 * HM row: esHerramientaMenor=true, cantidad=percentage (e.g. 5 = 5%),
 *          precioInsumo=null, rendimiento=null, cdAuxiliar=null.
 */
public record FilaSnapshot(
        SeccionTipo seccion,
        boolean esHerramientaMenor,
        BigDecimal cantidad,           // null iff esHerramientaMenor
        BigDecimal rendimiento,        // null for MATERIAL/TRANSPORTE and for HM row
        BigDecimal precioInsumo,       // null iff esHerramientaMenor
        BigDecimal overridePrecio,     // nullable; when non-null, overrides precioInsumo
        BigDecimal cdAuxiliar          // non-null iff row is an auxiliar reference (MATERIAL)
) {
    public FilaSnapshot {
        Objects.requireNonNull(seccion, "seccion must not be null");
        if (esHerramientaMenor && seccion != SeccionTipo.EQUIPO) {
            throw new IllegalArgumentException("HM row must be in EQUIPO section");
        }
    }
}
