package ec.uce.propuestas.cronograma.dto;

import ec.uce.propuestas.cronograma.export.CronogramaXlsxWriter;
import java.math.BigDecimal;
import java.util.List;

/**
 * Plan 031 (P-37) — proyección canónica inmutable que consume TODO el código
 * de presentación del cronograma valorizado. El
 * {@link ec.uce.propuestas.cronograma.export.CronogramaExportPreflightService}
 * y el {@link ec.uce.propuestas.cronograma.export.CronogramaProyeccionExportService}
 * son los únicos que producen este valor; los writers XLSX y PDF lo consumen
 * sin recalcular pesos, segmentos, montos ni totales (Plan 031 §Writers).
 *
 * <p>Las cuatro series de totales ({@code % PARCIAL}, {@code % ACUMULADO},
 * {@code MONTO PARCIAL}, {@code MONTO ACUMULADO}) están pre-computadas aquí
 * para que ambos writers no dupliquen la fórmula de suma fila-a-fila. La
 * inmutabilidad se preserva copiando las listas en el constructor (Java
 * records ya son superficialmente inmutables; las listas se copian para
 * evitar aliasing).</p>
 *
 * <p>Estructura: envuelve un {@link CronogramaXlsxWriter.ProyeccionXlsx} (que
 * carga la matriz fila × período) y suma por columna los valores que ya trae
 * la proyección. El writer sólo escribe — no recalcula. Esto cumple el
 * invariante del canon: <em>writers must consume ProyeccionExportacion
 * values only</em>.</p>
 */
public record ProyeccionExportacion(
        CronogramaXlsxWriter.ProyeccionXlsx hoja,
        List<BigDecimal> parcialPorcentaje,
        List<BigDecimal> acumuladoPorcentaje,
        List<BigDecimal> parcialMonto,
        List<BigDecimal> acumuladoMonto) {

    public ProyeccionExportacion {
        parcialPorcentaje = List.copyOf(parcialPorcentaje);
        acumuladoPorcentaje = List.copyOf(acumuladoPorcentaje);
        parcialMonto = List.copyOf(parcialMonto);
        acumuladoMonto = List.copyOf(acumuladoMonto);
    }

    /** Acceso de conveniencia al número de períodos. */
    public int numeroPeriodos() {
        return hoja.numeroPeriodos();
    }

    /**
     * Factory para tests y callers que aún producen una
     * {@link CronogramaXlsxWriter.ProyeccionXlsx} aislada y necesitan el
     * envoltorio canónico. Centraliza la fórmula Σ parcial / Σ acumulado
     * para que la implementación del cálculo viva en UN único lugar.
     */
    public static ProyeccionExportacion de(CronogramaXlsxWriter.ProyeccionXlsx hoja) {
        int n = hoja.numeroPeriodos();
        java.math.BigDecimal[] parcialPct = new java.math.BigDecimal[n];
        java.math.BigDecimal[] parcialMonto = new java.math.BigDecimal[n];
        for (int i = 0; i < n; i++) {
            parcialPct[i] = java.math.BigDecimal.ZERO.setScale(4);
            parcialMonto[i] = java.math.BigDecimal.ZERO.setScale(6);
        }
        for (CronogramaXlsxWriter.FilaHoja fh : hoja.filas()) {
            for (int i = 0; i < n; i++) {
                if (i < fh.porcentajesPorPeriodo().size()
                        && fh.porcentajesPorPeriodo().get(i) != null) {
                    parcialPct[i] =
                            parcialPct[i].add(fh.porcentajesPorPeriodo().get(i)).setScale(4);
                }
                java.math.BigDecimal monto = fh.montoPorPeriodo().get(i + 1);
                if (monto != null) {
                    parcialMonto[i] = parcialMonto[i].add(monto).setScale(6);
                }
            }
        }
        java.math.BigDecimal[] acumPct = new java.math.BigDecimal[n];
        java.math.BigDecimal[] acumMonto = new java.math.BigDecimal[n];
        java.math.BigDecimal corridoPct = java.math.BigDecimal.ZERO.setScale(4);
        java.math.BigDecimal corridoMonto = java.math.BigDecimal.ZERO.setScale(6);
        for (int i = 0; i < n; i++) {
            corridoPct = corridoPct.add(parcialPct[i]).setScale(4);
            corridoMonto = corridoMonto.add(parcialMonto[i]).setScale(6);
            acumPct[i] = corridoPct;
            acumMonto[i] = corridoMonto;
        }
        java.util.List<java.math.BigDecimal> listParcialPct =
                new java.util.ArrayList<>(java.util.Arrays.asList(parcialPct));
        java.util.List<java.math.BigDecimal> listAcumPct = new java.util.ArrayList<>(java.util.Arrays.asList(acumPct));
        java.util.List<java.math.BigDecimal> listParcialMonto =
                new java.util.ArrayList<>(java.util.Arrays.asList(parcialMonto));
        java.util.List<java.math.BigDecimal> listAcumMonto =
                new java.util.ArrayList<>(java.util.Arrays.asList(acumMonto));
        return new ProyeccionExportacion(hoja, listParcialPct, listAcumPct, listParcialMonto, listAcumMonto);
    }
}
