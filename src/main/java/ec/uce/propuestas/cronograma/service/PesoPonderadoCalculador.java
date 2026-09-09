package ec.uce.propuestas.cronograma.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Plan 028 — cierre canónico del peso ponderado fuera del motor
 * (02-data-model §16, fila {@code Peso_ponderado_i}).
 *
 * <p>Algoritmo, en unidades enteras de {@code 0.0001} (escala 4):
 * <ol>
 *   <li>Base por actividad: {@code precio_total_i / total_general × 100} a
 *       escala 4 {@code HALF_UP}. Con {@code total_general = 0} todas las bases
 *       son {@code 0.0000} y no hay residual que repartir.</li>
 *   <li>{@code residualUnits = 1000000 - Σ baseUnits}.</li>
 *   <li>Residual positivo: se suma completo a la primera actividad ordenada por
 *       {@code precio_total DESC, orden presupuestario}.</li>
 *   <li>Residual negativo: se consumen unidades siguiendo ese mismo orden sin
 *       que ningún peso baje de cero, hasta llegar exactamente a cero.</li>
 * </ol>
 *
 * <p>No se usa {@code double}, {@code float} ni división decimal sin escala. El
 * motor no se modifica: esta clase implementa únicamente el cierre residual que
 * el canon sitúa fuera de {@code motor/}.</p>
 */
public final class PesoPonderadoCalculador {

    /** 100.0000 en unidades de 0.0001. */
    private static final long TOTAL_UNITS = 1_000_000L;

    private PesoPonderadoCalculador() {}

    /**
     * Calcula los pesos ponderados a escala 4 en el mismo orden (presupuestario)
     * de {@code preciosTotales}.
     */
    public static List<BigDecimal> calcular(List<BigDecimal> preciosTotales, BigDecimal totalGeneral) {
        if (preciosTotales == null || preciosTotales.isEmpty()) {
            return List.of();
        }
        int n = preciosTotales.size();
        long[] units = new long[n];

        if (totalGeneral == null || totalGeneral.compareTo(BigDecimal.ZERO) == 0) {
            // Total cero conserva todos los pesos en 0.0000 (no se inventa distribución).
            return escalar(units);
        }

        for (int i = 0; i < n; i++) {
            BigDecimal precio = preciosTotales.get(i) == null ? BigDecimal.ZERO : preciosTotales.get(i);
            BigDecimal base = precio.multiply(BigDecimal.valueOf(100)).divide(totalGeneral, 4, RoundingMode.HALF_UP);
            units[i] = base.movePointRight(4).longValueExact();
        }

        long suma = 0;
        for (long u : units) {
            suma += u;
        }
        long residual = TOTAL_UNITS - suma;
        if (residual == 0) {
            return escalar(units);
        }

        List<Integer> orden = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            orden.add(i);
        }
        // precio_total DESC, empate por orden presupuestario (índice ascendente).
        orden.sort(Comparator.<Integer, BigDecimal>comparing(
                        i -> preciosTotales.get(i) == null ? BigDecimal.ZERO : preciosTotales.get(i))
                .reversed()
                .thenComparingInt(i -> i));

        if (residual > 0) {
            units[orden.get(0)] += residual;
        } else {
            long pendiente = -residual;
            for (int idx : orden) {
                if (pendiente == 0) {
                    break;
                }
                long consumible = Math.min(pendiente, units[idx]);
                units[idx] -= consumible;
                pendiente -= consumible;
            }
        }
        return escalar(units);
    }

    private static List<BigDecimal> escalar(long[] units) {
        List<BigDecimal> pesos = new ArrayList<>(units.length);
        for (long u : units) {
            pesos.add(BigDecimal.valueOf(u, 4));
        }
        return List.copyOf(pesos);
    }
}
