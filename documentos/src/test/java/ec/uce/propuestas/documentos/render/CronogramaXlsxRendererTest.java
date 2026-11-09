package ec.uce.propuestas.documentos.render;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1;
import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1.*;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class CronogramaXlsxRendererTest {
    private CronogramaProjectionV1 projection(String format) {
        var row = new SheetRow(
                "1",
                "=cmd",
                " \t+evil",
                "u",
                "2",
                "3.5",
                "7",
                "12.25",
                Arrays.asList(null, "0"),
                List.of(new PeriodAmount(2, "0")));
        var totals = new Totals(List.of("1", "2"), List.of("1", "3"), List.of("4", "5"), List.of("4", "9"));
        var sheet = new Sheet(
                "12/123456..abc",
                "Proyecto",
                2025,
                "SEMANA",
                2,
                "2025-01-01",
                "Institución",
                "Subdirección",
                2,
                4,
                List.of(row),
                List.of(new Chapter("A", "Capítulo", "7", 0)),
                totals);
        return new CronogramaProjectionV1(1, format, "pdf".equals(format) ? "a4" : null, sheet, null);
    }

    @Test
    void rendersCapturedValuesAndSafeCells() throws Exception {
        try (var wb =
                new XSSFWorkbook(new ByteArrayInputStream(CronogramaXlsxRenderer.renderizar(projection("xlsx"))))) {
            var sheet = wb.getSheetAt(0);
            assertEquals("12__abc", sheet.getSheetName());
            assertEquals("Cap. A", sheet.getRow(4).getCell(0).getStringCellValue());
            assertEquals(7, sheet.getRow(4).getCell(6).getNumericCellValue());
            var row = sheet.getRow(5);
            assertEquals("'=cmd", row.getCell(1).getStringCellValue());
            assertEquals("' \t+evil", row.getCell(2).getStringCellValue());
            assertEquals(CellType.NUMERIC, row.getCell(4).getCellType());
            assertEquals(CellType.STRING, row.getCell(8).getCellType());
            assertEquals("", row.getCell(8).getStringCellValue());
            assertEquals(CellType.NUMERIC, row.getCell(9).getCellType());
            assertEquals(0, row.getCell(9).getNumericCellValue());
            assertEquals("% PARCIAL", sheet.getRow(6).getCell(0).getStringCellValue());
            assertEquals(2, sheet.getRow(6).getCell(9).getNumericCellValue());
            assertEquals(9, sheet.getRow(9).getCell(9).getNumericCellValue());
        }
    }

    @Test
    void rejectsOtherVariant() {
        assertThrows(IllegalArgumentException.class, () -> CronogramaXlsxRenderer.renderizar(projection("pdf")));
    }
}
