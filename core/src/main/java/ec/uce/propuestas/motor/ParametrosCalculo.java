package ec.uce.propuestas.motor;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Parameters controlling how the motor computes an APU.
 * All percentages are in decimal form (e.g. 0.0500 = 5%, 0.1800 = 18%).
 *
 * <p>No-links (Plan 014): the per-APU {@code %CI} override is carried on
 * {@link ApuSnapshot#porcentajeIndirecto()} ({@code null} means
 * "inherit project default"). Plan 015 retired the per-APU discount seam
 * (P-24 / S-24 withdrawn): there is no {@code porcentajeDescuento} on
 * this record; the motor computes {@code CI = CD × %CI} and
 * {@code CT = CD + CI} unconditionally.
 */
public record ParametrosCalculo(
        BigDecimal porcentajeHerramientaMenor, // scale 4, e.g. 0.0500
        BigDecimal porcentajeIndirectoDefault // scale 4, from proyecto; null = no default
        ) {
    public ParametrosCalculo {
        Objects.requireNonNull(porcentajeHerramientaMenor, "porcentajeHerramientaMenor must not be null");
    }
}
