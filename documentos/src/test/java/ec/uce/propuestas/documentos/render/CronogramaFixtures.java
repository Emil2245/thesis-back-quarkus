package ec.uce.propuestas.documentos.render;

import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1;
import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1.Chapter;
import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1.PeriodAmount;
import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1.Sheet;
import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1.SheetRow;
import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1.Totals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds captured cronograma sheets for renderer tests ported from Core's retired writers (stage 05).
 * Totals follow the formula Core captures ({@code ProyeccionExportacion.de}): per-period partial sums of
 * active percentages (scale 4) and amounts (scale 6), then running accumulations. Renderers never
 * recompute them; the fixture only has to be internally consistent with the V1 contract.
 */
final class CronogramaFixtures {
    private CronogramaFixtures() {}

    /** A rubro row; {@code percentages} uses {@code null} for inactive periods. */
    static SheetRow row(
            String item,
            String code,
            String description,
            String unit,
            String quantity,
            String unitPrice,
            String totalPrice,
            String weight,
            List<String> percentages,
            Map<Integer, String> amounts) {
        var ordered = new TreeMap<>(amounts);
        var amountList = ordered.entrySet().stream()
                .map(e -> new PeriodAmount(e.getKey(), e.getValue()))
                .toList();
        return new SheetRow(
                item, code, description, unit, quantity, unitPrice, totalPrice, weight, percentages, amountList);
    }

    static List<String> percentages(String... values) {
        return Arrays.asList(values);
    }

    static Sheet sheet(
            String code,
            String name,
            int year,
            String unit,
            int periods,
            String start,
            List<SheetRow> rows,
            List<Chapter> chapters,
            String institution,
            String subdirection,
            int moneyPrecision,
            int percentagePrecision) {
        return new Sheet(
                code,
                name,
                year,
                unit,
                periods,
                start,
                institution,
                subdirection,
                moneyPrecision,
                percentagePrecision,
                rows,
                chapters,
                totals(periods, rows));
    }

    static Sheet sheet(String code, String name, int periods, List<SheetRow> rows) {
        return sheet(code, name, 2026, "SEMANA", periods, "2026-01-01", rows, List.of(), null, null, 2, 4);
    }

    static CronogramaProjectionV1 xlsx(Sheet sheet) {
        return new CronogramaProjectionV1(CronogramaProjectionV1.VERSION, "xlsx", null, sheet, null);
    }

    static CronogramaProjectionV1 pdf(Sheet sheet, String paper) {
        return new CronogramaProjectionV1(CronogramaProjectionV1.VERSION, "pdf", paper, sheet, null);
    }

    static Totals totals(int periods, List<SheetRow> rows) {
        var partialPct = new BigDecimal[periods];
        var partialAmount = new BigDecimal[periods];
        for (int i = 0; i < periods; i++) {
            partialPct[i] = BigDecimal.ZERO.setScale(4);
            partialAmount[i] = BigDecimal.ZERO.setScale(6);
        }
        for (SheetRow row : rows) {
            for (int i = 0; i < periods; i++) {
                String pct = row.percentByPeriod().get(i);
                if (pct != null)
                    partialPct[i] = partialPct[i].add(new BigDecimal(pct)).setScale(4);
            }
            for (PeriodAmount amount : row.amountByPeriod()) {
                int i = amount.period() - 1;
                partialAmount[i] =
                        partialAmount[i].add(new BigDecimal(amount.value())).setScale(6);
            }
        }
        var pp = new ArrayList<String>();
        var ap = new ArrayList<String>();
        var pm = new ArrayList<String>();
        var am = new ArrayList<String>();
        var runningPct = BigDecimal.ZERO.setScale(4);
        var runningAmount = BigDecimal.ZERO.setScale(6);
        for (int i = 0; i < periods; i++) {
            runningPct = runningPct.add(partialPct[i]).setScale(4);
            runningAmount = runningAmount.add(partialAmount[i]).setScale(6);
            pp.add(partialPct[i].toPlainString());
            ap.add(runningPct.toPlainString());
            pm.add(partialAmount[i].toPlainString());
            am.add(runningAmount.toPlainString());
        }
        return new Totals(pp, ap, pm, am);
    }
}
