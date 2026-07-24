package ec.uce.propuestas.motor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/** A rubro's scheduled advance percentages, one entry per period. */
public record ActividadSnapshot(
        String rubroCodigo,
        Map<Integer, BigDecimal> avancePorPeriodo   // periodo (1-based) → avance %
) {
    public ActividadSnapshot {
        Objects.requireNonNull(rubroCodigo, "rubroCodigo must not be null");
        Objects.requireNonNull(avancePorPeriodo, "avancePorPeriodo must not be null");
        avancePorPeriodo = Map.copyOf(avancePorPeriodo);
    }
}
