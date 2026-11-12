package ec.uce.propuestas.documentos.render;

import static ec.uce.propuestas.documentos.render.CronogramaFixtures.pdf;
import static ec.uce.propuestas.documentos.render.CronogramaFixtures.percentages;
import static ec.uce.propuestas.documentos.render.CronogramaFixtures.row;
import static ec.uce.propuestas.documentos.render.CronogramaFixtures.sheet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1.Chapter;
import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1.Sheet;
import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1.SheetRow;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

/**
 * Cronograma PDF format coverage ported in stage 05 from Core's retired {@code CronogramaPdfWriterTest}
 * (Plan 031 TC_P37_30..33 and the 52-week A4/A3 pagination evidence) and {@code CronogramaConformidadChkTest}
 * (CHK display precision and chapters). Characterization tests: the renderer already exists. Inactive periods
 * are {@code null} in the V1 capture (Core's old writer fixtures used {@code 0.0000}), so they print empty.
 */
class CronogramaPdfFormatTest {

    private static PDDocument render(Sheet sheet, String paper) throws IOException {
        return Loader.loadPDF(CronogramaPdfRenderer.renderizar(pdf(sheet, paper)));
    }

    private static Sheet base() {
        var row = row(
                "1.1",
                "R1",
                "Rubro uno",
                "u",
                "1.000000",
                "1.000000",
                "1.000000",
                "100.0000",
                percentages("50.0000", "50.0000"),
                Map.of(1, "1.000000", 2, "1.000000"));
        return sheet("PROYECTO-1", "Proyecto Demo", 2, List.of(row));
    }

    private static String strip(PDDocument doc) throws IOException {
        return new PDFTextStripper().getText(doc);
    }

    @Test
    void TC_P37_30_parseBackContainsTitleSummaryFooterAndIdentity() throws Exception {
        try (var doc = render(base(), "a4")) {
            assertNotNull(doc.getDocumentCatalog());
            assertEquals(1, doc.getNumberOfPages());
            String text = strip(doc);
            assertTrue(text.contains("CRONOGRAMA VALORADO DE TRABAJOS"));
            assertTrue(text.contains("PARCIAL"));
            assertTrue(text.contains("ACUMULADO"));
            assertTrue(text.contains("MONTO"));
            assertTrue(text.contains("Sistema APU"));
            assertTrue(text.contains("Proyecto Demo"));
        }
    }

    @Test
    void TC_P37_31_embedsFontInsteadOfDependingOnHostHelvetica() throws Exception {
        byte[] bytes = CronogramaPdfRenderer.renderizar(pdf(base(), "a4"));
        try (var doc = Loader.loadPDF(bytes)) {
            boolean declared = false;
            for (var page : doc.getPages()) {
                var resources = page.getResources();
                if (resources != null && resources.getFontNames() != null) declared = true;
            }
            assertTrue(declared);
            assertFalse(new String(bytes, StandardCharsets.ISO_8859_1).contains("Helvetica"));
        }
    }

    @Test
    void TC_P37_32_injectionIsPlainTextAndNeverALaunchAction() throws Exception {
        var hostile = row(
                "=cmd|/C calc",
                "=1+1",
                "+hack",
                "u",
                "1.000000",
                "1.000000",
                "1.000000",
                "100.0000",
                percentages((String) null),
                Map.of());
        byte[] bytes = CronogramaPdfRenderer.renderizar(
                pdf(sheet("PROYECTO-X", "Demo Injection PDF", 1, List.of(hostile)), "a4"));
        try (var doc = Loader.loadPDF(bytes)) {
            String text = strip(doc);
            assertTrue(text.contains("=cmd|/C calc") || text.contains("'=cmd"), text);
            assertFalse(text.toLowerCase().contains("/launchurl"));
            assertFalse(new String(bytes, StandardCharsets.ISO_8859_1).contains("/Launch"));
        }
    }

    @Test
    void TC_P37_33_paperSizesAreLandscapeWithoutRotation() throws Exception {
        for (String paper : List.of("a4", "a3")) {
            try (var doc = render(base(), paper)) {
                for (var page : doc.getPages()) {
                    var box = page.getMediaBox();
                    assertEquals("a4".equals(paper) ? 842f : 1191f, box.getWidth(), 0.5f, paper);
                    assertEquals("a4".equals(paper) ? 595f : 842f, box.getHeight(), 0.5f, paper);
                    assertEquals(0, page.getRotation(), paper);
                }
            }
        }
    }

    @Test
    void CHK_printsMoneyAtDisplayPrecisionAndChaptersInOrder() throws Exception {
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
        var sheet = sheet(
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
                2,
                4);
        try (var doc = render(sheet, "a4")) {
            String text = strip(doc);
            assertTrue(text.contains("1568.31"), text);
            assertTrue(text.contains("3678.65"));
            assertFalse(text.contains("1568.308000"));
            assertFalse(Pattern.compile("\\d+\\.\\d{6}\\b").matcher(text).find(), text);
            assertTrue(text.contains("75.1234"));
            assertTrue(text.contains("Cap. 1"));
            assertTrue(text.contains("Obras preliminares"));
            assertTrue(text.contains("Institución: IESS · Dirección Provincial Carchi"));
            assertTrue(text.indexOf("Cap. 1") < text.indexOf("Rubro uno"));
            assertTrue(text.indexOf("Rubro uno") < text.indexOf("Cap. 2"));
            assertTrue(text.indexOf("Cap. 2") < text.indexOf("Rubro dos"));
        }
    }

    /** Fifty equal weights; the final activity spans weeks 50–52. Inactive periods are null. */
    static List<SheetRow> fiftyTwoWeekRows() {
        var rows = new ArrayList<SheetRow>();
        for (int r = 0; r < 50; r++) {
            var pct = new String[52];
            var amounts = new LinkedHashMap<Integer, String>();
            if (r < 49) {
                pct[r] = "2.0000";
                amounts.put(r + 1, "20.000000");
            } else {
                for (int period = 50; period <= 52; period++) {
                    pct[period - 1] = period == 52 ? "0.6668" : "0.6666";
                    amounts.put(period, period == 52 ? "6.668000" : "6.666000");
                }
            }
            rows.add(row(
                    "1." + (r + 1),
                    "R" + (r + 1),
                    "Rubro " + (r + 1),
                    "u",
                    "1.000000",
                    "20.000000",
                    "20.000000",
                    "2.0000",
                    Arrays.asList(pct),
                    amounts));
        }
        return rows;
    }

    /** Expected captured series: partial %, accumulated %, partial amount, accumulated amount. */
    static List<List<String>> fiftyTwoWeekSeries() {
        var expected = new ArrayList<List<String>>();
        for (int s = 0; s < 4; s++) expected.add(new ArrayList<>());
        var pct = new BigDecimal("0.0000");
        var amount = new BigDecimal("0.000000");
        for (int period = 1; period <= 52; period++) {
            var partial = new BigDecimal(period <= 49 ? "2.0000" : period == 52 ? "0.6668" : "0.6666");
            var money = partial.multiply(BigDecimal.TEN).setScale(6);
            pct = pct.add(partial);
            amount = amount.add(money);
            expected.get(0).add(partial.toPlainString());
            expected.get(1).add(pct.toPlainString());
            expected.get(2).add(money.toPlainString());
            expected.get(3).add(amount.toPlainString());
        }
        assertEquals("100.0000", pct.toPlainString());
        assertEquals("1000.000000", amount.toPlainString());
        return expected;
    }

    @Test
    void fiftyTwoWeeksPaginateHorizontallyOnA4AndA3WithExactColumns() throws Exception {
        var rows = fiftyTwoWeekRows();
        var expected = fiftyTwoWeekSeries();
        var sheet = sheet("APU05-CRONO", "Evidencia 52 semanas", 52, rows);
        for (String paper : List.of("a4", "a3")) {
            try (var doc = render(sheet, paper)) {
                int covered = 0;
                String previousRange = "";
                for (int page = 1; page <= doc.getNumberOfPages(); page++) {
                    var reader = new PDFTextStripper();
                    reader.setStartPage(page);
                    reader.setEndPage(page);
                    String body = reader.getText(doc);
                    assertTrue(body.contains("Evidencia 52 semanas"), paper + " page " + page);
                    var range = Pattern.compile("Períodos (\\d+)–(\\d+) de 52").matcher(body);
                    assertTrue(range.find(), paper + " page " + page + " missing slice range");
                    int first = Integer.parseInt(range.group(1));
                    int last = Integer.parseInt(range.group(2));
                    if (!range.group().equals(previousRange)) {
                        assertEquals(covered + 1, first, "slices cover each week exactly once");
                        covered = last;
                        previousRange = range.group();
                    }
                    double width = (doc.getPage(page - 1).getMediaBox().getWidth() - 36 - 484) / (last - first + 1);
                    assertTrue(width >= 52, "readable period column width");
                    for (int period = first; period <= last; period++)
                        assertTrue(body.contains("Semana " + period), "continuation repeats schema");
                    assertTrue(body.contains("Rubro"), "no summary-only page");
                    assertTrue(body.contains("Sistema APU"), "footer on every page");
                }
                assertEquals(52, covered);
                for (var page : doc.getPages()) {
                    assertEquals(
                            "a4".equals(paper) ? 842f : 1191f,
                            page.getMediaBox().getWidth(),
                            0.5f);
                    assertEquals(
                            "a4".equals(paper) ? 595f : 842f, page.getMediaBox().getHeight(), 0.5f);
                }
                for (int period = 1; period <= 52; period++) {
                    String column = column(doc, period);
                    assertTrue(column.startsWith("Semana" + period), paper + " header " + period + ": " + column);
                    var values = new StringBuilder();
                    for (var row : rows) {
                        String pct = row.percentByPeriod().get(period - 1);
                        if (pct != null) values.append(pct);
                    }
                    for (int series = 0; series < 4; series++) {
                        var value = new BigDecimal(expected.get(series).get(period - 1));
                        values.append(
                                series < 2
                                        ? value.toPlainString()
                                        : value.setScale(2, RoundingMode.HALF_UP)
                                                .toPlainString());
                    }
                    assertEquals(values.toString(), column.replace("Semana" + period, ""), paper + " column " + period);
                }
                String labels = column(doc, 0);
                for (String label : List.of("%PARCIAL", "%ACUMULADO", "MONTOPARCIAL", "MONTOACUMULADO"))
                    assertTrue(labels.contains(label), paper + " missing summary " + label);
            }
        }
    }

    private static String column(PDDocument doc, int period) throws IOException {
        var text = new StringBuilder();
        for (int index = 0; index < doc.getNumberOfPages(); index++) {
            var page = doc.getPage(index);
            var reader = new PDFTextStripper();
            reader.setStartPage(index + 1);
            reader.setEndPage(index + 1);
            var range = Pattern.compile("Períodos (\\d+)–(\\d+) de 52").matcher(reader.getText(doc));
            assertTrue(range.find());
            int first = Integer.parseInt(range.group(1));
            int last = Integer.parseInt(range.group(2));
            if (period != 0 && (period < first || period > last)) continue;
            double width = (page.getMediaBox().getWidth() - 36 - 484) / (last - first + 1);
            double left = period == 0 ? 18 : 18 + 484 + (period - first) * width;
            double[] top = {0};
            var locator = new PDFTextStripper() {
                @Override
                protected void writeString(String value, List<TextPosition> positions) {
                    if (value.contains("Semana")) top[0] = positions.get(0).getYDirAdj() - 10;
                }
            };
            locator.setStartPage(index + 1);
            locator.setEndPage(index + 1);
            locator.getText(doc);
            assertTrue(top[0] > 0);
            var stripper = new PDFTextStripperByArea();
            stripper.addRegion(
                    "column",
                    new Rectangle2D.Double(
                            left,
                            top[0],
                            period == 0 ? 484 : width,
                            page.getMediaBox().getHeight() - top[0] - 24));
            stripper.extractRegions(page);
            text.append(stripper.getTextForRegion("column").replaceAll("\\s+", ""));
        }
        return text.toString();
    }
}
