package ec.uce.propuestas.motor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of one APU (Análisis de Precios Unitarios) as input to the motor.
 *
 * <p>No-links (Plan 014): this snapshot does <b>not</b> carry any
 * {@code esAuxiliar}/{@code cdAuxiliar} reference to another APU. Each APU is
 * an ordinary, independent price analysis. The per-APU {@code %CI} override
 * is carried as a nullable {@link BigDecimal} field: {@code null} means
 * "inherit the project default" ({@link ParametrosCalculo#porcentajeIndirectoDefault()}).
 */
public record ApuSnapshot(
        String codigo,
        BigDecimal porcentajeIndirecto, // scale 4, per-APU override; null = inherit project default
        List<FilaSnapshot> filas // in original workbook order
        ) {
    public ApuSnapshot {
        Objects.requireNonNull(codigo, "codigo must not be null");
        Objects.requireNonNull(filas, "filas must not be null");
        filas = List.copyOf(filas);
    }
}
