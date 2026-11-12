package ec.uce.propuestas.cronograma.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import ec.uce.propuestas.cronograma.dto.ProyeccionExportacion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CronogramaProjectionMapperTest {
    @Test
    void sheetCaptureRetainsInactiveCellsExplicitZeroOrderAndExactScale() {
        var amounts = new LinkedHashMap<Integer, BigDecimal>();
        amounts.put(3, new BigDecimal("0.000000"));
        amounts.put(1, new BigDecimal("12.340000"));
        var row = new HojaCronograma.FilaHoja(
                new HojaCronograma.Fila(
                        "1.1",
                        "R1",
                        "Captured",
                        "u",
                        new BigDecimal("1.000000"),
                        new BigDecimal("12.340000"),
                        new BigDecimal("12.340000"),
                        new BigDecimal("100.0000")),
                Arrays.asList(new BigDecimal("0.0000"), null, new BigDecimal("50.0000")),
                amounts);
        var hoja = new HojaCronograma.ProyeccionXlsx(
                "P1",
                "Project",
                2025,
                "SEMANA",
                3,
                LocalDate.of(2025, 1, 1),
                List.of(row),
                List.of(new HojaCronograma.CapituloHoja("1", "Chapter", new BigDecimal("12.340000"), 0)),
                "Institution",
                "Division",
                6,
                4);

        var captured = CronogramaProjectionMapper.fromSheet(ProyeccionExportacion.de(hoja), "xlsx", null);
        var transferred = captured.sheet().rows().getFirst();
        assertEquals("12.340000", transferred.unitPrice());
        assertEquals("0.0000", transferred.percentByPeriod().getFirst());
        assertNull(transferred.percentByPeriod().get(1));
        assertEquals(
                List.of(1, 3),
                transferred.amountByPeriod().stream().map(a -> a.period()).toList());
        assertEquals("12.340000", captured.sheet().totals().partialAmount().getFirst());
        assertEquals("2025-01-01", captured.sheet().startDate());
    }

    @Test
    void mspdiCaptureRetainsPublicUidAndActivePeriodsWithoutRecalculation() {
        UUID project = UUID.fromString("019a0000-0000-7000-8000-000000000001");
        UUID rubro = UUID.fromString("019a0000-0000-7000-8000-000000000002");
        var row = new MspdiCronograma.RubroMspdi(
                rubro,
                "1.1",
                "R1",
                "Captured",
                "u",
                new BigDecimal("1.000000"),
                new BigDecimal("12.340000"),
                new BigDecimal("12.340000"),
                new BigDecimal("100.0000"),
                List.of(2, 1),
                List.of(new BigDecimal("0.0000"), new BigDecimal("100.0000")));
        var source = new MspdiCronograma.ProyeccionMspdi(
                project, "P1", "Project", 2025, "SEMANA", 2, LocalDate.of(2025, 1, 1), List.of(row));

        var captured = CronogramaProjectionMapper.fromMspdi(source);
        assertEquals(project.toString(), captured.mspdi().projectUid());
        assertEquals(rubro.toString(), captured.mspdi().rows().getFirst().rubroUid());
        assertEquals(List.of(2, 1), captured.mspdi().rows().getFirst().activePeriods());
        assertEquals(
                List.of("0.0000", "100.0000"),
                captured.mspdi().rows().getFirst().percentages());
    }
}
