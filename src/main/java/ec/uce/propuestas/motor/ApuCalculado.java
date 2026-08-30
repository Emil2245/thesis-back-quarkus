package ec.uce.propuestas.motor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Fully computed result for one APU. All monetary values at scale 6.
 *
 * <p>No-links (Plan 014): no {@code esAuxiliar} flag — APUs are ordinary,
 * independent analyses. {@code costoIndirecto} is always derived from
 * {@code costoDirectoAjustado × %CI efectivo} using the cascading default
 * ({@link ApuSnapshot#porcentajeIndirecto()} → project default → 0).
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
        BigDecimal costoDirectoAjustado, // = CD × (1 - descuento)
        BigDecimal costoIndirecto, // = CD_ajustado × %CI efectivo
        BigDecimal costoTotal // = CD_ajustado + CI
        ) {
    public ApuCalculado {
        Objects.requireNonNull(codigo, "codigo must not be null");
        Objects.requireNonNull(filas, "filas must not be null");
        filas = List.copyOf(filas);
    }
}
