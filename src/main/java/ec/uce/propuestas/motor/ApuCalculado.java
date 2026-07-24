package ec.uce.propuestas.motor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Fully computed result for one APU. All monetary values at scale 6.
 */
public record ApuCalculado(
        String codigo,
        boolean esAuxiliar,
        List<FilaCalculada> filas,
        BigDecimal subtotalM,              // EQUIPO block including HM
        BigDecimal subtotalN,              // MANO_OBRA
        BigDecimal subtotalO,              // MATERIAL + auxiliar references
        BigDecimal subtotalP,              // TRANSPORTE
        BigDecimal costoHm,                // the HM row's costoFila (included in subtotalM)
        BigDecimal costoDirecto,           // = M + N + O + P
        BigDecimal costoDirectoAjustado,   // = CD × (1 - descuento)
        BigDecimal costoIndirecto,         // = CD_ajustado × %CI efectivo; 0 if auxiliar
        BigDecimal costoTotal              // = CD_ajustado + CI
) {
    public ApuCalculado {
        Objects.requireNonNull(codigo, "codigo must not be null");
        Objects.requireNonNull(filas, "filas must not be null");
        filas = List.copyOf(filas);
    }
}
