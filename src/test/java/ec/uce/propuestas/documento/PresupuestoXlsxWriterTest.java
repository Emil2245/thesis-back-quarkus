package ec.uce.propuestas.documento;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class PresupuestoXlsxWriterTest {
    @Test
    void fragmentaTextoSuperiorAlLimiteDeCeldaSinPerderUnicodeNiSaltos() {
        String source = "palabra ñ m² m³ 😀\n".repeat(2500) + "FIN";
        assertTrue(source.length() > 32767);
        var chunks = PresupuestoXlsxWriter.fragmentos(source);
        assertEquals(source, String.join("", chunks));
        assertTrue(chunks.size() > 1);
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 300);
            assertFalse(Character.isHighSurrogate(chunk.charAt(chunk.length() - 1)));
            assertFalse(Character.isLowSurrogate(chunk.charAt(0)));
        }
    }

    @Test
    void produceLibro() throws Exception {
        byte[] bytes = render(2, 0);
        assertTrue(bytes.length > 0, "Expected budget XLSX bytes; actual empty writer output");
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(1, wb.getNumberOfSheets());
        }
    }

    @Test
    void valoresPersistidosOrdenSeguridadYPrecision() throws Exception {
        for (int precision : new int[] {2, 4}) {
            byte[] bytes = render(precision, 0);
            try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                var sh = wb.getSheetAt(0);
                assertEquals("Presupuesto", sh.getSheetName());
                var fmt = new DataFormatter(java.util.Locale.US);
                String text = sh.getPhysicalNumberOfRows() + "";
                var codes = new java.util.ArrayList<String>();
                for (var row : sh) {
                    for (var cell : row) {
                        assertNotEquals(CellType.FORMULA, cell.getCellType());
                        assertNull(cell.getHyperlink());
                        text += "\n" + fmt.formatCellValue(cell);
                        if (cell.getColumnIndex() == 1
                                && cell.getCellType() == CellType.STRING
                                && !cell.getStringCellValue().isBlank()) codes.add(cell.getStringCellValue());
                    }
                    if (row.getCell(2) != null
                            && "Capítulo raíz".equals(row.getCell(2).getStringCellValue())) {
                        for (int c : new int[] {1, 3, 4, 5})
                            assertEquals(CellType.BLANK, row.getCell(c).getCellType());
                        assertEquals(
                                "888.888888", ((org.apache.poi.xssf.usermodel.XSSFCell) row.getCell(6)).getRawValue());
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
                assertEquals(java.util.List.of("-HIJO", "@PRIMERO", "+SEGUNDO", "DÉCIMO"), codes);
                for (String expected : new String[] {
                    "Dirección institucional",
                    "Subdirección",
                    "Proyecto histórico ñ",
                    "2025",
                    "Versión: 7",
                    PresupuestoDocumentoFixture.id(30).toString(),
                    PresupuestoDocumentoFixture.HOSTIL,
                    "sin IVA",
                    "Legal ñ",
                    "Técnico ñ",
                    precision == 2 ? "4321.99" : "4321.9877"
                }) assertTrue(text.contains(expected), expected);
                assertTrue(text.indexOf("Legal ñ") < text.indexOf("Técnico ñ"));
                assertFalse(text.contains("999999"));
                assertFalse(text.contains("888888"));
                assertNotNull(sh.getRepeatingRows());
                assertTrue(wb.getPrintArea(0).endsWith("$G$" + (sh.getLastRowNum() + 1)));
                assertTrue(sh.getColumnWidth(2) > sh.getColumnWidth(0));
                assertTrue(wb.getNumCellStyles() < 12);
            }
            Map<String, String> zip = zip(bytes);
            String xml = zip.get("xl/worksheets/sheet1.xml");
            assertTrue(xml.contains("<v>" + PresupuestoDocumentoFixture.EXACTO + "</v>"));
            assertTrue(xml.contains("<v>12.345678</v>"));
            assertTrue(xml.contains("<v>98.765432</v>"));
            assertTrue(xml.contains("<v>4321.987654</v>"));
            assertFalse(xml.contains("<f"));
            assertTrue(zip.keySet().stream().noneMatch(s -> s.contains("externalLink") || s.contains("vbaProject")));
        }
    }

    @Test
    void estilosAcotados() throws Exception {
        try (var small = new XSSFWorkbook(new ByteArrayInputStream(render(2, 0)));
                var large = new XSSFWorkbook(new ByteArrayInputStream(render(2, 140)))) {
            assertEquals(small.getNumCellStyles(), large.getNumCellStyles());
            assertEquals(
                    small.getSheetAt(0).getLastRowNum() + 140,
                    large.getSheetAt(0).getLastRowNum());
        }
    }

    @Test
    void omiteInstitucionAusenteSinInventarCampos() throws Exception {
        var s = PresupuestoDocumentoFixture.sinInstitucion();
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(
                PresupuestoXlsxWriter.renderizar(s, PresupuestoDocumentoFixture.opciones("xlsx", false))))) {
            var sh = wb.getSheetAt(0);
            assertEquals("PRESUPUESTO", sh.getRow(0).getCell(0).getStringCellValue());
            assertTrue(sh.getRow(1).getCell(0).getStringCellValue().startsWith("Proyecto:"));
        }
    }

    @Test
    void textoNumericoYDecimalesLimiteConservanTipoEscalaYMultiplicidad() throws Exception {
        var directory = java.nio.file.Path.of("build/bud03-evidence");
        java.nio.file.Files.createDirectories(directory);
        for (int precision : new int[] {0, 2}) {
            var snapshot = PresupuestoDocumentoFixture.extremo(precision);
            byte[] bytes =
                    PresupuestoXlsxWriter.renderizar(snapshot, PresupuestoDocumentoFixture.opciones("xlsx", false));
            java.nio.file.Files.write(directory.resolve("extreme-p" + precision + ".xlsx"), bytes);
            try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                var sheet = wb.getSheetAt(0);
                int financialRows = 0;
                for (var row : sheet)
                    if (row.getCell(4) != null && row.getCell(4).getCellType() == CellType.NUMERIC) financialRows++;
                assertEquals(snapshot.capitulos().getFirst().rubros().size(), financialRows);
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
                    assertTrue(row.getHeightInPoints() <= 240, "Reader-safe bounded row height");
                    if (continuationRows++ > 0) {
                        assertTrue(row.getCell(0).getStringCellValue().contains("continuación"));
                        for (int c : new int[] {1, 3, 4, 5, 6})
                            assertEquals(
                                    CellType.BLANK, row.getCell(c).getCellType(), "No duplicate identity or amounts");
                    }
                    if (value.contains("FINGIGANTE")) break;
                }
                assertTrue(continuationRows > 1, "Giant description needs physical continuation rows");
                assertEquals(
                        snapshot.capitulos().getFirst().rubros().stream()
                                .filter(r -> r.descripcion().contains("INICIOGIGANTE"))
                                .findFirst()
                                .orElseThrow()
                                .descripcion(),
                        recovered.toString());
                for (var rubro : snapshot.capitulos().getFirst().rubros()) {
                    int count = 0;
                    for (var row : wb.getSheetAt(0)) {
                        var code = row.getCell(1);
                        if (code == null
                                || code.getCellType() != CellType.STRING
                                || !rubro.codigo().equals(code.getStringCellValue())) continue;
                        count++;
                        assertEquals(CellType.STRING, code.getCellType());
                        for (int column = 4; column <= 6; column++) {
                            var expected =
                                    switch (column) {
                                        case 4 -> rubro.cantidad();
                                        case 5 -> rubro.precioUnitario();
                                        default -> rubro.precioTotal();
                                    };
                            var cell = (org.apache.poi.xssf.usermodel.XSSFCell) row.getCell(column);
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

    static byte[] render(int precision, int extras) {
        return PresupuestoXlsxWriter.renderizar(
                PresupuestoDocumentoFixture.snapshot(precision, extras),
                PresupuestoDocumentoFixture.opciones("xlsx", false));
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
