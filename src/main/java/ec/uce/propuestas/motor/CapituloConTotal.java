package ec.uce.propuestas.motor;

import java.math.BigDecimal;
import java.util.Objects;

/** A computed chapter with its aggregated total. */
public record CapituloConTotal(
        String item,
        int depth,
        BigDecimal total   // sum of all rubro precioTotal values recursively (scale 6)
) {
    public CapituloConTotal {
        Objects.requireNonNull(item, "item must not be null");
        Objects.requireNonNull(total, "total must not be null");
    }
}
