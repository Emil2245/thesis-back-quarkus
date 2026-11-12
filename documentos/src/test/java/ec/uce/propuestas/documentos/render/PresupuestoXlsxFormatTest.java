package ec.uce.propuestas.documentos.render;

import static ec.uce.propuestas.documentos.render.Projections.EXACTO;
import static ec.uce.propuestas.documentos.render.Projections.HOSTIL;
import static ec.uce.propuestas.documentos.render.Projections.id;
import static ec.uce.propuestas.documentos.render.Projections.load;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Budget XLSX format coverage ported in stage 05 from Core's retired {@code PresupuestoXlsxWriterTest} and the
 * budget golden of {@code ExportacionValoresNumericosOracleTest}. Same fixtures, same expected values.
 * Characterization tests: the renderer already exists.
 */
class PresupuestoXlsxFormatTest {

    static byte[] render(String fixture) {
        return PresupuestoXlsxRenderer.renderizar(load(fixture));
    }

    static List<String> numericCells(byte[] xlsx) throws Exception {
        var out = new ArrayList<String>();
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            for (var sheet : wb)
                for (var row : sheet)
                    for (var cell : row)
                        if (cell.getCellType() == CellType.NUMERIC)
                            out.add(((XSSFCell) cell).getCTCell().getV());
        }
        return out;
    }

    static final String GOLDEN_PRESUPUESTO =
            "4321.987654,4321.987654,1,1.23,1.23,1,1.23,1.23,1,1.23,1.23,1,1.23,1.23,4321.987654";

    @Test
    void oracleNumericSequenceIsUnchanged() throws Exception {
        assertEquals(GOLDEN_PRESUPUESTO, String.join(",", numericCells(render("presupuesto-hostil-xlsx"))));
    }

    @Test
    void producesSingleSheetWorkbook() throws Exception {
        byte[] bytes = render("presupuesto-p2-xlsx");
        assertTrue(bytes.length > 0);
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(1, wb.getNumberOfSheets());
        }
    }

    @Test
    void persistedValuesOrderSafetyAndPrecision() throws Exception {
        for (int precision : new int[] {2, 4}) {
            byte[] bytes = render("presupuesto-p" + precision + "-xlsx");
            try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                var sh = wb.getSheetAt(0);
                assertEquals("Presupuesto", sh.getSheetName());
                var fmt = new DataFormatter(Locale.US);
                var text = new StringBuilder();
                var codes = new ArrayList<String>();
                for (var row : sh) {
                    for (var cell : row) {
                        assertNotEquals(CellType.FORMULA, cell.getCellType());
                        assertNull(cell.getHyperlink());
                        text.append('\n').append(fmt.formatCellValue(cell));
                        if (cell.getColumnIndex() == 1
                                && cell.getCellType() == CellType.STRING
                                && !cell.getStringCellValue().isBlank()) codes.add(cell.getStringCellValue());
                    }
                    if (row.getCell(2) != null
                            && "Capítulo raíz".equals(row.getCell(2).getStringCellValue())) {
                        for (int c : new int[] {1, 3, 4, 5})
                            assertEquals(CellType.BLANK, row.getCell(c).getCellType());
                        assertEquals("888.888888", ((XSSFCell) row.getCell(6)).getRawValue());
                    }
                    if (row.getCell(1) != null
                            && row.getCell(1).getCellType() == CellType.STRING
                            && "@PRIMERO".equals(row.getCell(1).getStringCellValue())) {
                        assertEquals(CellType.NUMERIC, row.getCell(4).getCellType());
                        assertEquals(
                                precision == 2 ? "0.00" : "0.0000",
                                row.getCell(4).getCellStyle().getDataFormatString());
                        assertEquals(precision == 2 ? "12.35" : "12.3457", fmt.formatCellValue(row.getCell(5)));
                        assertTrue(row.getCell(2).getCellStyle().getWrapText());
                    }
                }
                codes.remove("Código");
                assertEquals(List.of("-HIJO", "@PRIMERO", "+SEGUNDO", "DÉCIMO"), codes);
                String all = text.toString();
                for (String expected : new String[] {
                    "Dirección institucional",
                    "Subdirección",
                    "Proyecto histórico ñ",
                    "2025",
                    "Versión: 7",
                    id(30).toString(),
                    HOSTIL,
                    "Nota: los precios no incluyen IVA",
                    "TABLA DE DESCRIPCIÓN DE RUBROS, UNIDADES, CANTIDADES Y PRECIOS",
                    "PROYECTO: Proyecto histórico ñ",
                    "Legal ñ",
                    "Técnico ñ",
                    precision == 2 ? "4321.99" : "4321.9877"
                }) assertTrue(all.contains(expected), expected);
                assertTrue(all.indexOf("Legal ñ") < all.indexOf("Técnico ñ"));
                assertFalse(all.contains("999999"));
                assertFalse(all.contains("888888"));
                assertNotNull(sh.getRepeatingRows());
                assertTrue(wb.getPrintArea(0).endsWith("$G$" + (sh.getLastRowNum() + 1)));
                assertTrue(sh.getColumnWidth(2) > sh.getColumnWidth(0));
                assertTrue(wb.getNumCellStyles() < 12);
            }
            Map<String, String> zip = zip(bytes);
            String xml = zip.get("xl/worksheets/sheet1.xml");
            assertTrue(xml.contains("<v>" + EXACTO + "</v>"));
            assertTrue(xml.contains("<v>12.345678</v>"));
            assertTrue(xml.contains("<v>98.765432</v>"));
            assertTrue(xml.contains("<v>4321.987654</v>"));
            assertFalse(xml.contains("<f"));
            assertTrue(zip.keySet().stream().noneMatch(s -> s.contains("externalLink") || s.contains("vbaProject")));
        }
    }

    @Test
    void boundedStylesRegardlessOfRowCount() throws Exception {
        try (var small = new XSSFWorkbook(new ByteArrayInputStream(render("presupuesto-p2-xlsx")));
                var large = new XSSFWorkbook(new ByteArrayInputStream(render("presupuesto-p2-x140-xlsx")))) {
            assertEquals(small.getNumCellStyles(), large.getNumCellStyles());
            assertEquals(
                    small.getSheetAt(0).getLastRowNum() + 140,
                    large.getSheetAt(0).getLastRowNum());
        }
    }

    @Test
    void coverLiteralsVatNoteAndSignerBlockInSpecOrder() throws Exception {
        var projection = load("presupuesto-firmantes-xlsx");
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(PresupuestoXlsxRenderer.renderizar(projection)))) {
            var col = new ArrayList<String>();
            for (var row : wb.getSheetAt(0))
                if (row.getCell(0) != null && row.getCell(0).getCellType() == CellType.STRING)
                    col.add(row.getCell(0).getStringCellValue());
            assertTrue(col.contains("TABLA DE DESCRIPCIÓN DE RUBROS, UNIDADES, CANTIDADES Y PRECIOS"));
            int total = col.indexOf("TOTAL");
            assertEquals("Nota: los precios no incluyen IVA", col.get(total + 1));
            var signers = col.subList(col.indexOf("CONSOLIDADO POR:"), col.size());
            String footer = projection.snapshot().parametros().mensajeFooter();
            assertEquals(
                    List.of(
                            "CONSOLIDADO POR:",
                            "Carla\nResponsable",
                            "FIRMA:",
                            "APROBADO POR:",
                            "Beto Uno\nAprobador",
                            "FIRMA:",
                            "Ana Dos\nRevisora",
                            "FIRMA:"),
                    signers.stream().filter(x -> !x.equals(footer)).toList());
        }
    }

    @Test
    void omitsMissingInstitutionWithoutInventingFields() throws Exception {
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(render("presupuesto-sin-institucion-xlsx")))) {
            var sh = wb.getSheetAt(0);
            assertEquals("PRESUPUESTO", sh.getRow(0).getCell(0).getStringCellValue());
            assertTrue(sh.getRow(1).getCell(0).getStringCellValue().startsWith("PROYECTO:"));
        }
    }

    /** L4: columns 4–6 carry each rubro's exact cantidad/PU/PT once, with item|código multiplicity 1. */
    @Test
    void numericTextAndLimitDecimalsKeepTypeScaleAndMultiplicity() throws Exception {
        for (int precision : new int[] {0, 2}) {
            DocumentProjectionV1 projection = load("presupuesto-extremo-p" + precision + "-xlsx");
            var rubros = projection.snapshot().capitulos().getFirst().rubros();
            byte[] bytes = PresupuestoXlsxRenderer.renderizar(projection);
            try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                var sheet = wb.getSheetAt(0);
                int financialRows = 0;
                for (var row : sheet)
                    if (row.getCell(4) != null && row.getCell(4).getCellType() == CellType.NUMERIC) financialRows++;
                assertEquals(rubros.size(), financialRows);
                var recovered = new StringBuilder();
                boolean giant = false;
                int continuationRows = 0;
                for (var row : sheet) {
                    var description = row.getCell(2);
                    if (description == null || description.getCellType() != CellType.STRING) continue;
                    String value = description.getStringCellValue();
                    if (value.contains("INICIOGIGANTE")) giant = true;
                    if (!giant) continue;
                    recovered.append(value);
                    assertTrue(row.getHeightInPoints() <= 240, "reader-safe bounded row height");
                    if (continuationRows++ > 0) {
                        assertTrue(row.getCell(0).getStringCellValue().contains("continuación"));
                        for (int c : new int[] {1, 3, 4, 5, 6})
                            assertEquals(CellType.BLANK, row.getCell(c).getCellType(), "no duplicate identity/amounts");
                    }
                    if (value.contains("FINGIGANTE")) break;
                }
                assertTrue(continuationRows > 1, "giant description needs continuation rows");
                assertEquals(
                        rubros.stream()
                                .filter(r -> r.descripcion().contains("INICIOGIGANTE"))
                                .findFirst()
                                .orElseThrow()
                                .descripcion(),
                        recovered.toString());
                for (var rubro : rubros) {
                    int count = 0;
                    for (var row : sheet) {
                        var code = row.getCell(1);
                        if (code == null
                                || code.getCellType() != CellType.STRING
                                || !rubro.codigo().equals(code.getStringCellValue())) continue;
                        count++;
                        for (int column = 4; column <= 6; column++) {
                            var expected = switch (column) {
                                case 4 -> rubro.cantidad();
                                case 5 -> rubro.precioUnitario();
                                default -> rubro.precioTotal();
                            };
                            var cell = (XSSFCell) row.getCell(column);
                            assertEquals(CellType.NUMERIC, cell.getCellType());
                            assertEquals(expected.toPlainString(), cell.getRawValue());
                            assertEquals(
                                    precision == 0 ? "0" : "0.00",
                                    cell.getCellStyle().getDataFormatString());
                        }
                    }
                    assertEquals(1, count, rubro.codigo());
                }
            }
        }
    }

    static Map<String, String> zip(byte[] bytes) throws Exception {
        var result = new HashMap<String, String>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry())
                result.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
        }
        return result;
    }
}
