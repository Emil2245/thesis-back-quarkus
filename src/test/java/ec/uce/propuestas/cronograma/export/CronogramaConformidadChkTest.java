package ec.uce.propuestas.cronograma.export;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.cronograma.dto.ProyeccionExportacion;
import ec.uce.propuestas.cronograma.export.CronogramaXlsxWriter.CapituloHoja;
import ec.uce.propuestas.cronograma.export.CronogramaXlsxWriter.Fila;
import ec.uce.propuestas.cronograma.export.CronogramaXlsxWriter.FilaHoja;
import ec.uce.propuestas.cronograma.export.CronogramaXlsxWriter.ProyeccionXlsx;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** CHK-22/28/34 (plan 034): capítulos como agrupación, institución en el encabezado y formato de display. */
class CronogramaConformidadChkTest {
    private static ProyeccionXlsx proyeccion(int precisionDinero, int precisionPorcentaje) {
        var f1 = new FilaHoja(
                new Fila(
                        "1.1",
                        "R1",
                        "Rubro uno",
                        "m2",
                        new BigDecimal("2.345678"),
                        new BigDecimal("1568.308000"),
                        new BigDecimal("3678.646143"),
                        new BigDecimal("75.1234")),
                List.of(new BigDecimal("50.0000"), new BigDecimal("25.1234")),
                Map.of(1, new BigDecimal("1839.323071"), 2, new BigDecimal("1839.323072")));
        var f2 = new FilaHoja(
                new Fila(
                        "2.1",
                        "R2",
                        "Rubro dos",
                        "u",
                        new BigDecimal("1.000000"),
                        new BigDecimal("1217.123456"),
                        new BigDecimal("1217.123456"),
                        new BigDecimal("24.8766")),
                List.of(new BigDecimal("0.0000"), new BigDecimal("24.8766")),
                Map.of(1, new BigDecimal("0.000000"), 2, new BigDecimal("1217.123456")));
        var caps = List.of(
                new CapituloHoja("1", "Obras preliminares", new BigDecimal("3678.646143"), 0),
                new CapituloHoja("2", "Estructura", new BigDecimal("1217.123456"), 1));
        return new ProyeccionXlsx(
                "TULCAN",
                "Cetro Médico Tulcán",
                2023,
                "MES",
                2,
                LocalDate.parse("2023-02-06"),
                List.of(f1, f2),
                caps,
                "IESS",
                "Dirección Provincial Carchi",
                precisionDinero,
                precisionPorcentaje);
    }

    @Test
    void xlsxFilasDeCapituloAgrupanSinSerActividades() throws Exception {
        var p = ProyeccionExportacion.de(proyeccion(2, 4));
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(CronogramaXlsxWriter.renderizar(p)))) {
            var sh = wb.getSheetAt(0);
            var col = new ArrayList<String>();
            for (var row : sh)
                col.add(row.getCell(0) == null ? "" : row.getCell(0).getStringCellValue());
            // header = fila 3; luego: capítulo 1, rubro 1.1, capítulo 2, rubro 2.1
            assertEquals(List.of("Cap. 1", "1.1", "Cap. 2", "2.1"), col.subList(4, 8));
            var cap = sh.getRow(4);
            assertEquals("Obras preliminares", cap.getCell(2).getStringCellValue());
            assertEquals(3678.646143, cap.getCell(6).getNumericCellValue(), 1e-9);
            // Sin datos de actividad: ni cantidad, ni PU, ni peso, ni períodos.
            for (int c : new int[] {1, 3, 4, 5, 7, 8, 9}) {
                var cell = cap.getCell(c);
                assertTrue(cell == null || cell.getCellType() != CellType.NUMERIC, "col " + c);
            }
        }
    }

    @Test
    void xlsxEncabezadoTrazaLaInstitucion() throws Exception {
        var p = ProyeccionExportacion.de(proyeccion(2, 4));
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(CronogramaXlsxWriter.renderizar(p)))) {
            assertEquals(
                    "Institución: IESS · Dirección Provincial Carchi",
                    wb.getSheetAt(0).getRow(2).getCell(0).getStringCellValue());
        }
    }

    @Test
    void xlsxFormatoDeCeldasSigueLaConfiguracionDeDisplayYNoCambiaElValor() throws Exception {
        for (int dinero : new int[] {2, 3}) {
            var p = ProyeccionExportacion.de(proyeccion(dinero, 4));
            try (var wb = new XSSFWorkbook(new ByteArrayInputStream(CronogramaXlsxWriter.renderizar(p)))) {
                var sh = wb.getSheetAt(0);
                String fmtDinero = dinero == 2 ? "0.00" : "0.000";
                var fila = sh.getRow(5);
                for (int c : new int[] {4, 5, 6})
                    assertEquals(fmtDinero, fila.getCell(c).getCellStyle().getDataFormatString(), "col " + c);
                assertEquals("0.0000", fila.getCell(7).getCellStyle().getDataFormatString());
                assertEquals("0.0000", fila.getCell(8).getCellStyle().getDataFormatString());
                // El valor exacto sigue en la celda (solo cambia la presentación).
                assertEquals(1568.308, fila.getCell(5).getNumericCellValue(), 1e-9);
                int resumen = 4 + 2 + 2; // cabecera(4) + 2 capítulos + 2 rubros
                assertEquals(
                        "0.0000", sh.getRow(resumen).getCell(8).getCellStyle().getDataFormatString());
                assertEquals(
                        fmtDinero,
                        sh.getRow(resumen + 2).getCell(8).getCellStyle().getDataFormatString());
                assertEquals("MONTO PARCIAL", sh.getRow(resumen + 2).getCell(0).getStringCellValue());
            }
        }
    }

    @Test
    void pdfImprimeDineroAPrecisionDeDisplayYCapitulos() throws Exception {
        var p = ProyeccionExportacion.de(proyeccion(2, 4));
        try (var pdf = Loader.loadPDF(CronogramaPdfWriter.renderizar(p))) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("1568.31"), text);
            assertTrue(text.contains("3678.65"));
            assertFalse(text.contains("1568.308000"));
            assertFalse(
                    java.util.regex.Pattern.compile("\\d+\\.\\d{6}\\b")
                            .matcher(text)
                            .find(),
                    text);
            assertTrue(text.contains("75.1234"));
            assertTrue(text.contains("Cap. 1"));
            assertTrue(text.contains("Obras preliminares"));
            assertTrue(text.contains("Institución: IESS · Dirección Provincial Carchi"));
            assertTrue(text.indexOf("Cap. 1") < text.indexOf("Rubro uno"));
            assertTrue(text.indexOf("Rubro uno") < text.indexOf("Cap. 2"));
            assertTrue(text.indexOf("Cap. 2") < text.indexOf("Rubro dos"));
        }
    }
}
