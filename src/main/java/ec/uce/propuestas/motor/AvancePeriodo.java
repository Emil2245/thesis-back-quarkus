package ec.uce.propuestas.motor;

import java.math.BigDecimal;
import java.util.Objects;

/** Cumulative schedule advance for a single period. */
public record AvancePeriodo(
        int periodo,
        BigDecimal avanceAcumuladoPct   // sum across all activities for this period (scale 4)
) {
    public AvancePeriodo {
        Objects.requireNonNull(avanceAcumuladoPct, "avanceAcumuladoPct must not be null");
    }
}
