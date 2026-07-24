package ec.uce.propuestas.motor.internal;

import ec.uce.propuestas.motor.FilaCalculada;
import ec.uce.propuestas.motor.FilaSnapshot;
import ec.uce.propuestas.motor.SeccionTipo;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Package-private helper. Computes the cost for one row based on its section type.
 * Never exposed outside the motor internal package.
 *
 * Row-level costs carry full BigDecimal precision (no intermediate setScale calls).
 * The Motor accumulates them and rounds only at the final output boundary.
 * This matches Excel workbook behavior, ensuring 0.00 deviation on golden masters.
 */
public final class CalculadorFila {

    private CalculadorFila() {}

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);
    static final int SCALE = 6;

    /** EQUIPO row (not HM): costoHora = cantidad × tarifa; costoFila = costoHora × rendimiento. */
    public static FilaCalculada calcularEquipo(FilaSnapshot f) {
        BigDecimal precio = effectivePrice(f);
        BigDecimal costoHora = f.cantidad().multiply(precio, MC);
        BigDecimal costoFila = costoHora.multiply(f.rendimiento(), MC);
        return new FilaCalculada(SeccionTipo.EQUIPO, false, f.cantidad(), f.rendimiento(),
                precio,
                costoHora.setScale(SCALE, RoundingMode.HALF_UP),
                costoFila);
    }

    /** MANO_OBRA row: costoHora = cantidad × jornal; costoFila = costoHora × rendimiento. */
    public static FilaCalculada calcularManoObra(FilaSnapshot f) {
        BigDecimal precio = effectivePrice(f);
        BigDecimal costoHora = f.cantidad().multiply(precio, MC);
        BigDecimal costoFila = costoHora.multiply(f.rendimiento(), MC);
        return new FilaCalculada(SeccionTipo.MANO_OBRA, false, f.cantidad(), f.rendimiento(),
                precio,
                costoHora.setScale(SCALE, RoundingMode.HALF_UP),
                costoFila);
    }

    /**
     * MATERIAL row: costoFila = cantidad × COALESCE(cdAuxiliar, overridePrecio, precioInsumo).
     * No costoHora for materials.
     */
    public static FilaCalculada calcularMaterial(FilaSnapshot f) {
        BigDecimal precio = f.cdAuxiliar() != null ? f.cdAuxiliar() : effectivePrice(f);
        BigDecimal costoFila = f.cantidad().multiply(precio, MC);
        return new FilaCalculada(SeccionTipo.MATERIAL, false, f.cantidad(), null,
                precio, null, costoFila);
    }

    /**
     * TRANSPORTE row: costoFila = cantidad × precio. No rendimiento multiplication.
     */
    public static FilaCalculada calcularTransporte(FilaSnapshot f) {
        BigDecimal precio = effectivePrice(f);
        BigDecimal costoFila = f.cantidad().multiply(precio, MC);
        return new FilaCalculada(SeccionTipo.TRANSPORTE, false, f.cantidad(), null,
                precio, null, costoFila);
    }

    /** COALESCE(overridePrecio, precioInsumo). */
    private static BigDecimal effectivePrice(FilaSnapshot f) {
        return f.overridePrecio() != null ? f.overridePrecio() : f.precioInsumo();
    }
}
