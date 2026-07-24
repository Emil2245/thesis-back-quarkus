package ec.uce.propuestas.motor;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Computed result for one line (fila) in an APU.
 */
public record FilaCalculada(
        SeccionTipo seccion,
        boolean esHerramientaMenor,
        BigDecimal cantidad,
        BigDecimal rendimiento,
        BigDecimal precioUnitarioEfectivo,   // COALESCE(overridePrecio, precioInsumo, cdAuxiliar)
        BigDecimal costoHora,                // EQUIPO/MO only: cantidad × precio; null for MATERIAL/TRANSPORTE/HM
        BigDecimal costoFila                 // final row cost
) {
    public FilaCalculada {
        Objects.requireNonNull(seccion, "seccion must not be null");
        Objects.requireNonNull(costoFila, "costoFila must not be null");
    }
}
