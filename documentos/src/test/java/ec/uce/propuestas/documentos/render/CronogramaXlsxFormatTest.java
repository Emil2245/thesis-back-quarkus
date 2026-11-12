package ec.uce.propuestas.documentos.render;

import static ec.uce.propuestas.documentos.render.CronogramaFixtures.percentages;
import static ec.uce.propuestas.documentos.render.CronogramaFixtures.row;
import static ec.uce.propuestas.documentos.render.CronogramaFixtures.sheet;
import static ec.uce.propuestas.documentos.render.CronogramaFixtures.xlsx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1.Chapter;
import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1.Sheet;
import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1.SheetRow;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Cronograma XLSX format coverage ported in stage 05 from Core's retired {@code CronogramaXlsxWriterTest}
 * (Plan 031 TC_P37_20/21/23/24, NFR-PER-02), {@code CronogramaConformidadChkTest} (CHK-22/28/34) and the
 * 52-period evidence of {@code CronogramaPdfWriterTest}. Characterization tests: the renderer already exists.
 */
class CronogramaXlsxFormatTest {

    private static XSSFWorkbook render(Sheet sheet) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(CronogramaXlsxRenderer.renderizar(xlsx(sheet))));
    }

    private static Sheet base() {
        var first = row(
                "1.1",
                "R1",
                "Rubro uno",
                "u",
                "1.000000",
                "1.000000",
                "1.000000",
                "50.0000",
                percentages("50.0000", "50.0000", null, null),
                Map.of(1, "0.500000", 2, "0.500000"));
        var second = row(
                "1.2",
                "R2",
                "Rubro dos",
                "u",
                "1.000000",
                "2.000000",
                "2.000000",
                "50.0000",
                percentages(null, null, "50.0000", "50.0000"),
                Map.of(3, "0.500000", 4, "0.500000"));
        return sheet("PROYECTO-1", "Proyecto Demo", 4, List.of(first, second));
    }

    /** CHK-22/28/34 fixture: two chapters, institution, two months. */
    private static Sheet chk(int moneyPrecision, int percentagePrecision) {
        var first = row(
                "1.1",
                "R1",
                "Rubro uno",
                "m2",
                "2.345678",
                "1568.308000",
                "3678.646143",
                "75.1234",
                percentages("50.0000", "25.1234"),
                Map.of(1, "1839.323071", 2, "1839.323072"));
        var second = row(
                "2.1",
                "R2",
                "Rubro dos",
                "u",
                "1.000000",
                "1217.123456",
                "1217.123456",
                "24.8766",
                percentages("0.0000", "24.8766"),
                Map.of(1, "0.000000", 2, "1217.123456"));
        var chapters = List.of(
                new Chapter("1", "Obras preliminares", "3678.646143", 0),
                new Chapter("2", "Estructura", "1217.123456", 1));
        return sheet(
                "TULCAN",
                "Cetro Médico Tulcán",
                2023,
                "MES",
                2,
                "2023-02-06",
                List.of(first, second),
                chapters,
                "IESS",
                "Dirección Provincial Carchi",
                moneyPrecision,
                percentagePrecision);
    }

    private static String text(XSSFSheet sheet) {
        var sb = new StringBuilder();
        for (var row : sheet) for (var cell : row) sb.append(cell).append('\n');
        return sb.toString();
    }

    @Test
    void TC_P37_20_validWorkbookWithCanonicalTitleIdentityAndSummaryRows() throws Exception {
        try (var wb = render(base())) {
            var sh = wb.getSheetAt(0);
            assertNotNull(sh);
            assertTrue(sh.getRow(0).getCell(0).getStringCellValue().contains("CRONOGRAMA VALORADO DE TRABAJOS"));
            String text = text(sh);
            assertTrue(text.contains("Proyecto Demo"));
            for (String label : List.of("% PARCIAL", "% ACUMULADO", "MONTO PARCIAL", "MONTO ACUMULADO"))
                assertTrue(text.contains(label), label);
        }
    }

    @Test
    void TC_P37_21_fixedColumnsThenOnePeriodColumnInOrder() throws Exception {
        try (var wb = render(base())) {
            var header = wb.getSheetAt(0).getRow(3);
            var columns = new ArrayList<String>();
            for (var cell : header) columns.add(cell.getStringCellValue());
            assertEquals(
                    List.of(
                            "Item",
                            "Código",
                            "Descripción",
                            "Unidad",
                            "Cantidad",
                            "P.Unitario",
                            "P.Total",
                            "Peso",
                            "Semana 1",
                            "Semana 2",
                            "Semana 3",
                            "Semana 4"),
                    columns);
        }
    }

    @Test
    void TC_P37_23_sheetNameSanitizedWithoutPathTraversalOrLongNumbers() throws Exception {
        try (var wb = render(sheet("../../etc/passwd", "Proyecto/./Demo", 1, List.of()))) {
            String name = wb.getSheetName(0);
            assertFalse(name.contains("/"), name);
            assertFalse(name.contains(".."), name);
            assertFalse(name.matches(".*\\d{6,}.*"), name);
            assertTrue(name.length() <= 31, name);
        }
    }

    @Test
    void TC_P37_24_formulaInjectionIsNeutralizedInTextCells() throws Exception {
        var hostile = row(
                "=SUM(A1:A2)",
                "=1+1",
                "+CMD|/C calc!A0",
                "u",
                "1.000000",
                "1.000000",
                "1.000000",
                "100.0000",
                percentages((String) null),
                Map.of());
        try (var wb = render(sheet("PROYECTO-X", "Demo Formula Injection", 1, List.of(hostile)))) {
            var row = wb.getSheetAt(0).getRow(4);
            assertEquals("'=SUM(A1:A2)", row.getCell(0).getStringCellValue());
            assertEquals("'=1+1", row.getCell(1).getStringCellValue());
            assertEquals("'+CMD|/C calc!A0", row.getCell(2).getStringCellValue());
            for (var r : wb.getSheetAt(0))
                for (var cell : r) assertFalse(cell.getCellType() == CellType.FORMULA, "no formula cells");
        }
    }

    @Test
    void rowsAreWrittenInCapturedOrderNotLexicographically() throws Exception {
        var rows = new ArrayList<SheetRow>();
        var items = new ArrayList<String>();
        for (int i = 1; i <= 12; i++) {
            items.add("1." + i);
            rows.add(row(
                    "1." + i,
                    "R" + i,
                    "Rubro " + i,
                    "u",
                    "1.000000",
                    "1.000000",
                    "1.000000",
                    "1.0000",
                    percentages("1.0000"),
                    Map.of(1, "1.000000")));
        }
        try (var wb = render(sheet("ORDEN", "Orden natural", 1, rows))) {
            var sh = wb.getSheetAt(0);
            var printed = new ArrayList<String>();
            for (int r = 4; r < 4 + 12; r++) printed.add(sh.getRow(r).getCell(0).getStringCellValue());
            assertEquals(items, printed);
        }
    }

    @Test
    void CHK_22_chapterRowsGroupWithoutActivityData() throws Exception {
        try (var wb = render(chk(2, 4))) {
            var sh = wb.getSheetAt(0);
            var col = new ArrayList<String>();
            for (var row : sh)
                col.add(row.getCell(0) == null ? "" : row.getCell(0).getStringCellValue());
            assertEquals(List.of("Cap. 1", "1.1", "Cap. 2", "2.1"), col.subList(4, 8));
            var chapter = sh.getRow(4);
            assertEquals("Obras preliminares", chapter.getCell(2).getStringCellValue());
            assertEquals(3678.646143, chapter.getCell(6).getNumericCellValue(), 1e-9);
            for (int c : new int[] {1, 3, 4, 5, 7, 8, 9}) {
                var cell = chapter.getCell(c);
                assertTrue(cell == null || cell.getCellType() != CellType.NUMERIC, "col " + c);
            }
        }
    }

    @Test
    void CHK_28_headerTracesInstitution() throws Exception {
        try (var wb = render(chk(2, 4))) {
            assertEquals(
                    "Institución: IESS · Dirección Provincial Carchi",
                    wb.getSheetAt(0).getRow(2).getCell(0).getStringCellValue());
        }
    }

    @Test
    void CHK_34_cellFormatFollowsDisplayPrecisionWithoutChangingValue() throws Exception {
        for (int money : new int[] {2, 3}) {
            try (var wb = render(chk(money, 4))) {
                var sh = wb.getSheetAt(0);
                String moneyFormat = money == 2 ? "0.00" : "0.000";
                var row = sh.getRow(5);
                for (int c : new int[] {4, 5, 6})
                    assertEquals(moneyFormat, row.getCell(c).getCellStyle().getDataFormatString(), "col " + c);
                assertEquals("0.0000", row.getCell(7).getCellStyle().getDataFormatString());
                assertEquals("0.0000", row.getCell(8).getCellStyle().getDataFormatString());
                assertEquals(1568.308, row.getCell(5).getNumericCellValue(), 1e-9);
                int summary = 4 + 2 + 2;
                assertEquals(
                        "0.0000", sh.getRow(summary).getCell(8).getCellStyle().getDataFormatString());
                assertEquals(
                        moneyFormat,
                        sh.getRow(summary + 2).getCell(8).getCellStyle().getDataFormatString());
                assertEquals("MONTO PARCIAL", sh.getRow(summary + 2).getCell(0).getStringCellValue());
            }
        }
    }

    @Test
    void fiftyTwoWeeksKeepExactRawValuesForEveryRowAndSummarySeries() throws Exception {
        var rows = CronogramaPdfFormatTest.fiftyTwoWeekRows();
        var sheet = sheet("APU05-CRONO", "Evidencia 52 semanas", 52, rows);
        var expected = CronogramaPdfFormatTest.fiftyTwoWeekSeries();
        assertEquals(expected.get(0), sheet.totals().partialPercentage());
        assertEquals(expected.get(1), sheet.totals().accumulatedPercentage());
        assertEquals(expected.get(2), sheet.totals().partialAmount());
        assertEquals(expected.get(3), sheet.totals().accumulatedAmount());
        try (var wb = render(sheet)) {
            var sh = wb.getSheetAt(0);
            for (int period = 0; period < 52; period++) {
                assertEquals(
                        "Semana " + (period + 1),
                        sh.getRow(3).getCell(8 + period).getStringCellValue());
                for (int r = 0; r < 50; r++) {
                    String pct = rows.get(r).percentByPeriod().get(period);
                    var cell = (XSSFCell) sh.getRow(4 + r).getCell(8 + period);
                    if (pct == null) {
                        assertEquals("", cell.getStringCellValue(), "inactive period stays empty");
                    } else {
                        assertEquals(0, new BigDecimal(pct).compareTo(new BigDecimal(cell.getRawValue())));
                    }
                }
                for (int series = 0; series < 4; series++) {
                    var cell = (XSSFCell) sh.getRow(54 + series).getCell(8 + period);
                    assertEquals(
                            0,
                            new BigDecimal(expected.get(series).get(period))
                                    .compareTo(new BigDecimal(cell.getRawValue())));
                }
            }
        }
    }

    @Test
    void NFR_PER_02_twoHundredRowsTwelvePeriodsUnderFiveSeconds() {
        int rubros = 200;
        int periods = 12;
        String weight = new BigDecimal("100.0000")
                .divide(new BigDecimal(rubros), 4, RoundingMode.HALF_UP)
                .toPlainString();
        String pct = new BigDecimal("100.0000")
                .divide(new BigDecimal(periods), 4, RoundingMode.HALF_UP)
                .toPlainString();
        String amount = new BigDecimal("100.000000")
                .divide(new BigDecimal(periods), 6, RoundingMode.HALF_UP)
                .toPlainString();
        var rows = new ArrayList<SheetRow>();
        for (int i = 0; i < rubros; i++) {
            var amounts = new LinkedHashMap<Integer, String>();
            for (int p = 1; p <= periods; p++) amounts.put(p, amount);
            rows.add(row(
                    "1." + (i + 1),
                    "R-" + (i + 1),
                    "Rubro " + (i + 1),
                    "u",
                    "1.000000",
                    "100.000000",
                    "100.000000",
                    weight,
                    new ArrayList<>(Collections.nCopies(periods, pct)),
                    amounts));
        }
        long start = System.nanoTime();
        byte[] bytes =
                CronogramaXlsxRenderer.renderizar(xlsx(sheet("P31-PERF", "Performance 200 rubros", periods, rows)));
        long millis = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(millis < 5_000L, "NFR-PER-02 <5s, took " + millis + "ms");
        assertTrue(bytes.length > 10_000, "200-row XLSX should exceed 10KB, was " + bytes.length);
    }
}
