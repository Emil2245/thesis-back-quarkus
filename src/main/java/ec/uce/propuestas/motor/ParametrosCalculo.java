package ec.uce.propuestas.motor;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Parameters controlling how the motor computes an APU.
 * All percentages are in decimal form (e.g. 0.0500 = 5%, 0.1800 = 18%).
 */
public record ParametrosCalculo(
        BigDecimal porcentajeHerramientaMenor,   // scale 4, e.g. 0.0500
        BigDecimal porcentajeIndirectoDefault,   // scale 4, from proyecto; null = no default
        BigDecimal porcentajeIndirectoApu,       // scale 4, from apu; null = inherit default
        BigDecimal porcentajeDescuento           // scale 4, e.g. 0.1000 for 10%
) {
    public ParametrosCalculo {
        Objects.requireNonNull(porcentajeHerramientaMenor, "porcentajeHerramientaMenor must not be null");
        Objects.requireNonNull(porcentajeDescuento, "porcentajeDescuento must not be null");
    }
}
