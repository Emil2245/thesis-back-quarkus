package ec.uce.propuestas.documentos.contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Value-only captured cronograma; Core calculates every number before crossing the service boundary. */
public record CronogramaProjectionV1(int schemaVersion, String format, String paper, Sheet sheet, Mspdi mspdi) {
    public static final int VERSION = 1;

    public CronogramaProjectionV1 {
        if (schemaVersion != VERSION || format == null || (sheet == null) == (mspdi == null)) {
            throw new IllegalArgumentException("Invalid cronograma projection version or variant");
        }
        if (mspdi != null && (!"mspdi".equals(format) || paper != null)) {
            throw new IllegalArgumentException("Invalid MSPDI projection options");
        }
        if (sheet != null
                && (!("xlsx".equals(format) || "pdf".equals(format))
                        || ("xlsx".equals(format) && paper != null)
                        || ("pdf".equals(format) && !("a4".equals(paper) || "a3".equals(paper))))) {
            throw new IllegalArgumentException("Invalid cronograma sheet options");
        }
    }

    public record PeriodAmount(int period, String value) {}

    /** Null percentage cells represent inactive periods, unlike an explicit active zero. */
    public record SheetRow(
            String item,
            String code,
            String description,
            String unit,
            String quantity,
            String unitPrice,
            String totalPrice,
            String weight,
            List<String> percentByPeriod,
            List<PeriodAmount> amountByPeriod) {
        public SheetRow {
            percentByPeriod = Collections.unmodifiableList(new ArrayList<>(percentByPeriod));
            amountByPeriod = List.copyOf(amountByPeriod);
        }
    }

    public record Chapter(String item, String description, String total, int beforeRow) {}

    public record Totals(
            List<String> partialPercentage,
            List<String> accumulatedPercentage,
            List<String> partialAmount,
            List<String> accumulatedAmount) {
        public Totals {
            partialPercentage = List.copyOf(partialPercentage);
            accumulatedPercentage = List.copyOf(accumulatedPercentage);
            partialAmount = List.copyOf(partialAmount);
            accumulatedAmount = List.copyOf(accumulatedAmount);
        }
    }

    public record Sheet(
            String projectCode,
            String projectName,
            int year,
            String timeUnit,
            int periodCount,
            String startDate,
            String institution,
            String subdirection,
            int moneyPrecision,
            int percentagePrecision,
            List<SheetRow> rows,
            List<Chapter> chapters,
            Totals totals) {
        public Sheet {
            rows = List.copyOf(rows);
            chapters = List.copyOf(chapters);
        }
    }

    public record MspdiRow(
            String rubroUid,
            String item,
            String code,
            String description,
            String unit,
            String quantity,
            String unitPrice,
            String totalPrice,
            String weight,
            List<Integer> activePeriods,
            List<String> percentages) {
        public MspdiRow {
            activePeriods = List.copyOf(activePeriods);
            percentages = List.copyOf(percentages);
        }
    }

    public record Mspdi(
            String projectUid,
            String projectCode,
            String projectName,
            int year,
            String timeUnit,
            int periodCount,
            String startDate,
            List<MspdiRow> rows) {
        public Mspdi {
            rows = List.copyOf(rows);
        }
    }
}
