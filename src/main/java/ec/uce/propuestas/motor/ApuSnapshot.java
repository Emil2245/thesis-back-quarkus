package ec.uce.propuestas.motor;

import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of one APU (Análisis de Precios Unitarios) as input to the motor.
 */
public record ApuSnapshot(
        String codigo,
        boolean esAuxiliar,
        List<FilaSnapshot> filas   // in original workbook order
) {
    public ApuSnapshot {
        Objects.requireNonNull(codigo, "codigo must not be null");
        Objects.requireNonNull(filas, "filas must not be null");
        filas = List.copyOf(filas);
    }
}
