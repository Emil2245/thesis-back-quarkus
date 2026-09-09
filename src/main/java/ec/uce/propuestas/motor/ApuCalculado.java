package ec.uce.propuestas.motor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Fully computed result for one APU. All monetary values at scale 6.
 *
 * <p>No-links (Plan 014): no {@code esAuxiliar} flag — APUs are ordinary,
 * independent analyses. Plan 015 retired the per-APU discount seam
 * (P-24 / S-24 withdrawn): there is no {@code costoDirectoAjustado} on
 * this record. {@code costoIndirecto} is derived from {@code CD × %CI efectivo}
 * using the cascading default ({@link ApuSnapshot#porcentajeIndirecto()}
 * → project default → 0); {@code CT = CD + CI}.
 */
public record ApuCalculado(
        String codigo,
        List<FilaCalculada> filas,
        BigDecimal subtotalM, // EQUIPO block including HM
        BigDecimal subtotalN, // MANO_OBRA
        BigDecimal subtotalO, // MATERIAL + auxiliar references
        BigDecimal subtotalP, // TRANSPORTE
        BigDecimal costoHm, // the HM row's costoFila (included in subtotalM)
        BigDecimal costoDirecto, // = M + N + O + P
        BigDecimal costoIndirecto, // = CD × %CI efectivo
        BigDecimal costoTotal // = CD + CI
        ) {
    public ApuCalculado {
        Objects.requireNonNull(codigo, "codigo must not be null");
        Objects.requireNonNull(filas, "filas must not be null");
        filas = List.copyOf(filas);
    }
}
