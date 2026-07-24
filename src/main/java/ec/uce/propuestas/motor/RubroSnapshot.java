package ec.uce.propuestas.motor;

import java.math.BigDecimal;
import java.util.Objects;

/** One rubro (work item) in a budget version. */
public record RubroSnapshot(
        String codigo,
        BigDecimal cantidad,
        ApuSnapshot apu
) {
    public RubroSnapshot {
        Objects.requireNonNull(codigo, "codigo must not be null");
        Objects.requireNonNull(cantidad, "cantidad must not be null");
        Objects.requireNonNull(apu, "apu must not be null");
    }
}
