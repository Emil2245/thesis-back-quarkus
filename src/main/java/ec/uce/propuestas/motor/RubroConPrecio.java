package ec.uce.propuestas.motor;

import java.math.BigDecimal;
import java.util.Objects;

/** A computed rubro with its unit and total price. */
public record RubroConPrecio(
        String codigo,
        BigDecimal cantidad,
        BigDecimal precioUnitario,   // = apu.costoTotal (scale 6)
        BigDecimal precioTotal       // = cantidad × precioUnitario (scale 6)
) {
    public RubroConPrecio {
        Objects.requireNonNull(codigo, "codigo must not be null");
    }
}
