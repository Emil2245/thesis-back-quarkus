package ec.uce.propuestas.motor;

import java.math.BigDecimal;
import java.util.Objects;

/** Weighted percentage of a rubro relative to the total budget. Scale 4 (e.g. 0.3971 = 0.3971%). */
public record PesoPonderado(
        String rubroCodigo,
        BigDecimal pesoPct   // precioTotal / totalGeneral × 100, scale 4
) {
    public PesoPonderado {
        Objects.requireNonNull(rubroCodigo, "rubroCodigo must not be null");
        Objects.requireNonNull(pesoPct, "pesoPct must not be null");
    }
}
