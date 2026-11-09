package ec.uce.propuestas.cronograma.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CronogramaProjectionV1Test {
    @Test
    void rejectsIncompleteOrAmbiguousPeriodShapes() {
        var row = new CronogramaProjectionV1.SheetRow(
                "1.1",
                "R1",
                "Captured",
                "u",
                "1.000000",
                "12.340000",
                "12.340000",
                "100.0000",
                List.of("100.0000"),
                List.of(new CronogramaProjectionV1.PeriodAmount(1, "12.340000")));
        var totals = new CronogramaProjectionV1.Totals(
                List.of("100.0000", "0.0000"), List.of("100.0000", "100.0000"),
                List.of("12.340000", "0.000000"), List.of("12.340000", "12.340000"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CronogramaProjectionV1.Sheet(
                        "P1",
                        "Project",
                        2025,
                        "SEMANA",
                        2,
                        "2025-01-01",
                        "Institution",
                        "Division",
                        6,
                        4,
                        List.of(row),
                        List.of(),
                        totals));
        var duplicate = new CronogramaProjectionV1.SheetRow(
                "1.1",
                "R1",
                "Captured",
                "u",
                "1.000000",
                "12.340000",
                "12.340000",
                "100.0000",
                List.of("100.0000", "0.0000"),
                List.of(
                        new CronogramaProjectionV1.PeriodAmount(1, "12.340000"),
                        new CronogramaProjectionV1.PeriodAmount(1, "0.000000")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CronogramaProjectionV1.Sheet(
                        "P1",
                        "Project",
                        2025,
                        "SEMANA",
                        2,
                        "2025-01-01",
                        "Institution",
                        "Division",
                        6,
                        4,
                        List.of(duplicate),
                        List.of(),
                        totals));
        var outside = new CronogramaProjectionV1.SheetRow(
                "1.1",
                "R1",
                "Captured",
                "u",
                "1.000000",
                "12.340000",
                "12.340000",
                "100.0000",
                List.of("100.0000", "0.0000"),
                List.of(new CronogramaProjectionV1.PeriodAmount(3, "12.340000")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CronogramaProjectionV1.Sheet(
                        "P1",
                        "Project",
                        2025,
                        "SEMANA",
                        2,
                        "2025-01-01",
                        "Institution",
                        "Division",
                        6,
                        4,
                        List.of(outside),
                        List.of(),
                        totals));
        var wrongCounts = new CronogramaProjectionV1.MspdiRow(
                "019a0000-0000-7000-8000-000000000001",
                "1.1",
                "R1",
                "Captured",
                "u",
                "1.000000",
                "12.340000",
                "12.340000",
                "100.0000",
                List.of(1, 2),
                List.of("100.0000"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CronogramaProjectionV1.Mspdi(
                        "019a0000-0000-7000-8000-000000000002",
                        "P1",
                        "Project",
                        2025,
                        "SEMANA",
                        2,
                        "2025-01-01",
                        List.of(wrongCounts)));
    }

    @Test
    void jsonRoundTripPreservesCapturedOrderInactivePeriodsAndExactDecimalText() throws Exception {
        var amounts = new ArrayList<>(List.of(new CronogramaProjectionV1.PeriodAmount(1, "12.340000")));
        var percentages = new ArrayList<String>();
        percentages.add("100.0000");
        percentages.add(null);
        var rows = new ArrayList<>(List.of(new CronogramaProjectionV1.SheetRow(
                "1.1", "R1", "Captured", "u", "1.000000", "12.340000", "12.340000", "100.0000", percentages, amounts)));
        var sheet = new CronogramaProjectionV1.Sheet(
                "P1",
                "Project",
                2025,
                "SEMANA",
                2,
                "2025-01-01",
                "Institution",
                "Division",
                6,
                4,
                rows,
                List.of(new CronogramaProjectionV1.Chapter("1", "Chapter", "12.340000", 0)),
                new CronogramaProjectionV1.Totals(
                        List.of("100.0000", "0.0000"), List.of("100.0000", "100.0000"),
                        List.of("12.340000", "0.000000"), List.of("12.340000", "12.340000")));
        var captured = new CronogramaProjectionV1(CronogramaProjectionV1.VERSION, "xlsx", null, sheet, null);
        rows.clear();
        amounts.clear();
        percentages.set(0, "0.0000");

        var mapper = new ObjectMapper().findAndRegisterModules();
        String json = mapper.writeValueAsString(captured);
        var decoded = mapper.readValue(json, CronogramaProjectionV1.class);
        assertEquals(1, decoded.sheet().rows().size());
        assertEquals(
                "100.0000", decoded.sheet().rows().getFirst().percentByPeriod().getFirst());
        assertEquals(null, decoded.sheet().rows().getFirst().percentByPeriod().get(1));
        assertEquals(
                "12.340000",
                decoded.sheet().rows().getFirst().amountByPeriod().getFirst().value());
        assertFalse(json.contains("usuarioId"));
        assertFalse(json.contains("presupuestoId"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> decoded.sheet().rows().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> decoded.sheet().rows().getFirst().percentByPeriod().clear());
    }
}
