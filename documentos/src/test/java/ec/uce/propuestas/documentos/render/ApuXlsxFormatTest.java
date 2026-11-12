package ec.uce.propuestas.documentos.render;

import static ec.uce.propuestas.documentos.render.ApuFixtures.as;
import static ec.uce.propuestas.documentos.render.ApuFixtures.hourly;
import static ec.uce.propuestas.documentos.render.ApuFixtures.xlsx;
import static ec.uce.propuestas.documentos.render.Projections.EXACTO;
import static ec.uce.propuestas.documentos.render.Projections.load;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.ApuView;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * APU XLSX format coverage ported in stage 05 from Core's retired {@code ApuXlsxWriterTest},
 * {@code ExportacionValoresNumericosOracleTest} (APU goldens), {@code ApuIdentidadFirmasTest} (CHK-26/27),
 * {@code ApuDocumentoConciliacionTest} (CHK-05/06/07 workbook case) and the XLSX parity/stress checks of
 * {@code ApuDocumentoEvidenceIT}. Same fixtures through the real Core mapper, same expected values.
 * Characterization tests: the renderer already exists.
 */
class ApuXlsxFormatTest {
    private static final String[] LAYOUTS = {"pestanas", "apilado"};

    /** Same constant as Core's {@code ApuDocumentoFixture.TEXTO}. */
    private static final String TEXTO = "=<script> ñ 😃 []:*?/\\ @SUM(A1)\n";

    static final String GOLDEN_APU_HOSTIL =
            "2.000001,9.000000001,10.000000002,0.000123456,12.000000003,2.000001,9.000000001,10.000000002,0.000123456,12.000000003,2.000001,9.000000001,10.000000002,0.000123456,12.000000003,11.123456789,0,2.000001,9.000000001,12.000000003,22.000000,0,11.123456789,0,22.000000,0,33.123456789,0.1800,4.987654321,123.123456789012345678901234567890,123.123456789012345678901234567890,2.000001,9.000000001,10.000000002,0.000123456,12.000000003,2.000001,9.000000001,10.000000002,0.000123456,12.000000003,2.000001,9.000000001,10.000000002,0.000123456,12.000000003,11.123456789,0,2.000001,9.000000001,12.000000003,22.000000,0,11.123456789,0,22.000000,0,33.123456789,0.1800,4.987654321,123.123456789012345678901234567890,123.123456789012345678901234567890";
    static final String GOLDEN_APU_CONCILIADO =
            "5.00,0.000000,0.289875,1,15.00,15.000000,0.33,4.950000,5.239875,1,4.75,4.750000,0.33,1.567500,2,4.23,8.460000,0.5,4.230000,5.797500,3.5,1.2345,4.320750,4.320750,0.5,2.00,1.000000,1.000000,5.239875,5.797500,4.320750,1.000000,16.358125,0.1800,2.944463,19.302588,19.302588,5.00,0.000000,0.289875,1,15.00,15.000000,0.33,4.950000,5.239875,1,4.75,4.750000,0.33,1.567500,2,4.23,8.460000,0.5,4.230000,5.797500,3.5,1.2345,4.320750,4.320750,0.5,2.00,1.000000,1.000000,5.239875,5.797500,4.320750,1.000000,16.358125,0.1800,2.944463,19.302588,19.302588";

    private static byte[] render(DocumentProjectionV1 projection) {
        return ApuXlsxRenderer.renderizar(projection);
    }

    @Test
    void oracleNumericSequencesAreUnchanged() throws Exception {
        assertEquals(
                GOLDEN_APU_HOSTIL,
                String.join(",", PresupuestoXlsxFormatTest.numericCells(render(load("apus-hostil-pestanas")))));
        assertEquals(
                GOLDEN_APU_CONCILIADO,
                String.join(",", PresupuestoXlsxFormatTest.numericCells(render(load("apus-conciliado-pestanas")))));
    }

    // ---- L4: ordered numeric parity with the projection, sheet count, no formulas, no external links ----

    record NumberCell(int column, String value) {}

    private static void number(List<NumberCell> cells, int column, BigDecimal value) {
        if (value != null) cells.add(new NumberCell(column, value.toPlainString()));
    }

    /** The printed oracle: every number of an APU in print order and column (from ApuDocumentoEvidenceIT). */
    static List<NumberCell> expected(ApuView a) {
        var cells = new ArrayList<NumberCell>();
        for (var b : a.bloques()) {
            boolean hourly = hourly(b.tipo());
            for (var row : b.filas()) {
                var d = row.detalle();
                number(cells, hourly ? 1 : 2, d.cantidad());
                number(cells, hourly ? 2 : 3, d.precioEfectivo());
                if (hourly) {
                    number(cells, 3, row.costoHora());
                    number(cells, 4, d.rendimiento());
                }
                number(cells, 5, row.costo());
            }
            if (b.mostrarSubtotal()) number(cells, 5, b.subtotal());
        }
        for (var s : a.pie().subtotales()) number(cells, 5, s.valor());
        number(cells, 5, a.pie().costoDirecto());
        number(cells, 4, a.pie().porcentajeIndirecto());
        number(cells, 5, a.pie().costoIndirecto());
        number(cells, 5, a.pie().costoTotal());
        number(cells, 5, a.pie().valorOfertado());
        return cells;
    }

    private static void parity(DocumentProjectionV1 source) throws Exception {
        var apus = source.snapshot().apuRender();
        for (String layout : LAYOUTS) {
            boolean stacked = layout.equals("apilado");
            try (var wb = new XSSFWorkbook(new ByteArrayInputStream(render(xlsx(source, layout))))) {
                assertEquals(stacked ? 1 : apus.size(), wb.getNumberOfSheets(), layout);
                assertTrue(wb.getExternalLinksTable().isEmpty());
                var all = new ArrayList<NumberCell>();
                for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                    var actual = new ArrayList<NumberCell>();
                    for (var row : wb.getSheetAt(i))
                        for (var cell : row) {
                            assertNotEquals(CellType.FORMULA, cell.getCellType());
                            if (cell.getCellType() == CellType.NUMERIC)
                                actual.add(new NumberCell(cell.getColumnIndex(), ((XSSFCell) cell).getRawValue()));
                        }
                    if (stacked) all.addAll(actual);
                    else
                        assertEquals(
                                expected(apus.get(i)),
                                actual,
                                "UUID " + apus.get(i).id());
                }
                if (stacked)
                    assertEquals(
                            apus.stream().flatMap(a -> expected(a).stream()).toList(),
                            all,
                            "ordered exact OOXML values, columns and multiplicity");
            }
        }
    }

    @Test
    void numericCellsMatchProjectionInPrintOrderForCapturedFixtures() throws Exception {
        for (String fixture : List.of("apus-hostil-pestanas", "apus-conciliado-pestanas", "apus-identidad-pestanas"))
            parity(load(fixture));
    }

    @Test
    void stressThreeApusOf125NegativeRowsAtPrecisionZeroAndTwo() throws Exception {
        for (int precision : new int[] {0, 2}) parity(ApuFixtures.extremes(precision));
    }

    // ---- CHK-26/27: project identity and signer block on every APU sheet ----

    private static final List<String> FIRMAS = List.of(
            "CONSOLIDADO POR:",
            "Carla\nResponsable",
            "FIRMA:",
            "APROBADO POR:",
            "Beto Uno\nAprobador",
            "FIRMA:",
            "Ana Dos\nRevisora",
            "FIRMA:");

    @Test
    void everySheetCarriesProjectIdentityAndSignersInOrder() throws Exception {
        var projection = load("apus-identidad-pestanas");
        var proyecto = projection.snapshot().proyecto();
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(render(projection)))) {
            assertEquals(2, wb.getNumberOfSheets());
            for (var sheet : wb) {
                var col = new ArrayList<String>();
                for (var row : sheet)
                    if (row.getCell(0) != null && row.getCell(0).getCellType() == CellType.STRING)
                        col.add(row.getCell(0).getStringCellValue());
                col.removeIf(x -> x.startsWith("APU UUID: ")
                        || x.startsWith("APU código: ")
                        || x.startsWith("APU descripción: "));
                assertTrue(col.contains("PROYECTO: " + proyecto.nombre()), col.toString());
                assertTrue(col.contains("Año: " + proyecto.anio()));
                assertTrue(col.contains(proyecto.direccionInstitucional()));
                int from = col.indexOf("CONSOLIDADO POR:");
                assertTrue(from > col.indexOf("Este precio no incluye IVA"));
                assertEquals(FIRMAS, col.subList(from, col.size()));
            }
        }
    }

    // ---- CHK-05/06/07: stale APU without HM prints rows that add up to the subtotal and no HM row ----

    @Test
    void staleApuWithoutHmRowsSumToSubtotalAndNoHmRow() throws Exception {
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(render(load("apus-vencido-sinhm-pestanas"))))) {
            var sheet = wb.getSheetAt(0);
            BigDecimal sum = BigDecimal.ZERO;
            int hm = 0;
            boolean inBlock = false;
            boolean checked = false;
            for (var row : sheet) {
                String a = row.getCell(0) == null ? "" : row.getCell(0).getStringCellValue();
                if (a.equals("EQUIPOS (M)")) {
                    inBlock = true;
                    sum = BigDecimal.ZERO;
                    continue;
                }
                if (!inBlock) continue;
                if (a.startsWith("Subtotal EQUIPOS")) {
                    var subtotal = new BigDecimal(
                            ((XSSFCell) row.getCell(5)).getCTCell().getV());
                    assertEquals(0, sum.compareTo(subtotal), "Σ rows M = subtotal M");
                    checked = true;
                    break;
                }
                if (a.equals("Descripción")) continue;
                if (a.startsWith("Herramienta Menor")) hm++;
                if (row.getCell(5) != null && row.getCell(5).getCellType() == CellType.NUMERIC)
                    sum = sum.add(new BigDecimal(
                            ((XSSFCell) row.getCell(5)).getCTCell().getV()));
            }
            assertTrue(checked, "EQUIPOS subtotal row found");
            assertEquals(0, hm);
        }
    }

    // ---- ApuXlsxWriterTest cases on Core fixtures ----

    private static List<XSSFRow> rows(XSSFWorkbook wb) {
        var result = new ArrayList<XSSFRow>();
        for (var sheet : wb) {
            var sections = new HashSet<String>();
            String identity = "";
            int skipSchema = -1;
            for (var row : sheet) {
                var x = (XSSFRow) row;
                if (printedIdentity(x)) {
                    if (label(x).startsWith("APU UUID: ") && !identity.equals(label(x))) {
                        identity = label(x);
                        sections.clear();
                    }
                    continue;
                }
                if (x.getRowNum() == skipSchema) continue;
                var next = (XSSFRow) sheet.getRow(x.getRowNum() + 1);
                boolean heading = next != null
                        && label(next).equals("Descripción")
                        && next.getCell(5) != null
                        && next.getCell(5).getStringCellValue().equals("Costo")
                        && wb.getFontAt(x.getCell(0).getCellStyle().getFontIndex())
                                .getBold();
                if (heading && !sections.add(label(x))) {
                    skipSchema = next.getRowNum();
                    continue;
                }
                result.add(x);
            }
        }
        return result;
    }

    private static boolean printedIdentity(XSSFRow row) {
        String text = label(row);
        int offset = text.startsWith("APU UUID: ")
                ? 0
                : text.startsWith("APU código: ") ? 1 : text.startsWith("APU descripción: ") ? 2 : -1;
        if (offset < 0 || row.getRowNum() < offset) return false;
        var start = row.getSheet().getRow(row.getRowNum() - offset);
        return start != null && label(start).matches("APU UUID: [0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    }

    private static String label(XSSFRow row) {
        var c = row.getCell(0);
        return c == null ? "" : c.getStringCellValue();
    }

    private static void exact(XSSFRow row, int column, BigDecimal value) {
        var cell = row.getCell(column);
        assertEquals(CellType.NUMERIC, cell.getCellType());
        assertEquals(value.toPlainString(), cell.getCTCell().getV());
    }

    /**
     * Ported without the old {@code diagnostico().costoTotal() != pie().costoTotal()} check: the motor diagnostic is
     * Core-only and never crosses the V1 boundary.
     */
    @Test
    void hmKeepsOrderEffectivePercentageAndPersistedAmount() throws Exception {
        var source = load("apus-ci024680-pestanas");
        for (String layout : LAYOUTS)
            try (var wb = new XSSFWorkbook(new ByteArrayInputStream(render(xlsx(source, layout))))) {
                var all = rows(wb);
                var hm = all.stream()
                        .filter(r -> label(r).equals("Herramienta Menor 7.300%MO"))
                        .toList();
                assertEquals(2, hm.size());
                for (var row : hm) {
                    int position = all.indexOf(row);
                    assertEquals(TEXTO, label(all.get(position - 1)));
                    assertEquals(TEXTO, label(all.get(position - 2)));
                    exact(row, 5, new BigDecimal("12.000000003"));
                    exact(row, 1, new BigDecimal("2.000001"));
                    exact(row, 2, new BigDecimal("9.000000001"));
                    exact(row, 3, new BigDecimal("10.000000002"));
                }
                var ci = all.stream()
                        .filter(r -> label(r).equals("Costo indirecto"))
                        .toList();
                exact(ci.get(0), 4, new BigDecimal("0.1800"));
                exact(ci.get(1), 4, new BigDecimal("0.24680"));
                assertTrue(all.stream()
                        .filter(r -> label(r).equals(TEXTO))
                        .allMatch(r -> r.getCell(0).getCellType() == CellType.STRING));
            }
    }

    private static String seguro(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Cc}&&[^\\n\\t]]", "");
    }

    @Test
    void independentTogglesAndConfiguredSigners() throws Exception {
        for (int mask : new int[] {0, 1, 2, 4, 8, 16, 32, 64, 127}) {
            boolean empty = (mask & 1) != 0, suffix = (mask & 2) != 0, section = (mask & 4) != 0;
            boolean footerTotals = (mask & 8) != 0, project = (mask & 16) != 0, enumerate = (mask & 32) != 0;
            boolean footer = (mask & 64) != 0;
            var source = load("apus-toggles-" + mask + "-pestanas");
            var apus = source.snapshot().apuRender();
            var signers = load("apus-toggles-64-pestanas").snapshot().firmantes();
            for (String layout : LAYOUTS)
                try (var wb = new XSSFWorkbook(new ByteArrayInputStream(render(xlsx(source, layout))))) {
                    var labels = rows(wb).stream().map(ApuXlsxFormatTest::label).toList();
                    String ctx = "mask " + mask + " " + layout;
                    assertEquals(
                            2,
                            labels.stream()
                                    .filter(l -> l.equals("ANÁLISIS DE PRECIOS UNITARIOS"))
                                    .count(),
                            ctx);
                    assertEquals(
                            project ? 2 : 0,
                            labels.stream()
                                    .filter(l -> l.startsWith("Proyecto:"))
                                    .count(),
                            ctx);
                    assertEquals(
                            footer ? 2 : 0,
                            labels.stream().filter(l -> l.equals("@mensaje")).count(),
                            ctx);
                    int blockCount = empty ? 4 : 2;
                    assertEquals(
                            section ? blockCount * 2 : 0,
                            labels.stream()
                                    .filter(l -> l.startsWith("Subtotal ")
                                            && !l.matches("Subtotal (EQUIPO|MANO_OBRA|MATERIAL|TRANSPORTE)"))
                                    .count(),
                            ctx);
                    assertEquals(
                            footerTotals ? 8 : 0,
                            labels.stream()
                                    .filter(l -> l.matches("Subtotal (EQUIPO|MANO_OBRA|MATERIAL|TRANSPORTE)"))
                                    .count(),
                            ctx);
                    for (int i = 0; i < apus.size(); i++) {
                        assertTrue(
                                labels.contains("Código: " + (enumerate ? (i + 1) + " " : "")
                                        + apus.get(i).codigo()),
                                ctx);
                        var blocks = apus.get(i).bloques();
                        for (int b = 0; b < blocks.size(); b++) {
                            String etiqueta = blocks.get(b).etiqueta();
                            assertEquals(suffix, etiqueta.matches(".* \\([MNOP]\\)$"), ctx);
                            assertTrue(labels.contains(etiqueta), ctx);
                            if (b > 0)
                                assertTrue(
                                        labels.indexOf(blocks.get(b - 1).etiqueta()) < labels.indexOf(etiqueta), ctx);
                        }
                    }
                    assertEquals(
                            empty ? 2 : 0,
                            labels.stream()
                                    .filter(l -> l.startsWith("MANO DE OBRA"))
                                    .count(),
                            ctx);
                    assertEquals(
                            empty ? 2 : 0,
                            labels.stream()
                                    .filter(l -> l.startsWith("TRANSPORTE"))
                                    .count(),
                            ctx);
                    for (var f : signers) {
                        String name = seguro(f.nombre()) + "\n" + seguro(f.cargo());
                        assertEquals(
                                footer ? 2 : 0,
                                labels.stream().filter(name::equals).count(),
                                ctx);
                    }
                }
        }
    }

    @Test
    void emptyProjectionInventsNoApus() throws Exception {
        var empty = ApuFixtures.apus(load("apus-hostil-pestanas"), List.of());
        for (String layout : LAYOUTS)
            try (var wb = new XSSFWorkbook(new ByteArrayInputStream(render(xlsx(empty, layout))))) {
                assertEquals(0, wb.getNumberOfSheets());
            }
    }

    @Test
    void rejectsForeignOptions() {
        var empty = ApuFixtures.apus(load("apus-hostil-pestanas"), List.of());
        assertThrows(IllegalArgumentException.class, () -> render(as(empty, "pdf", Map.of())));
        assertThrows(
                IllegalArgumentException.class, () -> render(as(empty, "xlsx", Map.of("orientacion", "vertical"))));
    }

    @Test
    void keepsIdentitiesAndPersistedCostsInBothLayouts() throws Exception {
        var source = load("apus-hostil-pestanas");
        for (String layout : LAYOUTS) {
            try (var wb = new XSSFWorkbook(new ByteArrayInputStream(render(xlsx(source, layout))))) {
                assertEquals(layout.equals("pestanas") ? 2 : 1, wb.getNumberOfSheets());
                int costs = 0;
                int totals = 0;
                for (var sheet : wb) {
                    assertEquals(0, sheet.getPrintSetup().getFitHeight());
                    assertEquals(1, sheet.getPrintSetup().getFitWidth());
                    for (var row : sheet)
                        for (var cell : row) {
                            assertNotEquals(CellType.FORMULA, cell.getCellType());
                            assertNull(cell.getHyperlink());
                            if (cell instanceof XSSFCell x && x.getCTCell().isSetV()) {
                                if ("12.000000003".equals(x.getCTCell().getV())) costs++;
                                if (EXACTO.equals(x.getCTCell().getV())) totals++;
                            }
                        }
                }
                assertEquals(8, costs);
                assertEquals(4, totals);
            }
        }
    }
}
