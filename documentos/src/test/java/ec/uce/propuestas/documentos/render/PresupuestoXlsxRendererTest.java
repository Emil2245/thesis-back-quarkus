package ec.uce.propuestas.documentos.render;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.*;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class PresupuestoXlsxRendererTest {
    @Test
    void rendersProjectedValuesAndStructure() throws Exception {
        var id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var rubro = new Rubro(
                id,
                id,
                "1.1",
                "=COD",
                "Descripción",
                "m",
                new BigDecimal("2.123456"),
                new BigDecimal("3.123456"),
                new BigDecimal("9.123456"));
        var capitulo =
                new Capitulo(id, "1", "Capítulo", (short) 1, new BigDecimal("99.123456"), List.of(), List.of(rubro));
        var proyecto = new Proyecto(id, "Proyecto", "P", null, (short) 2025, null, null, null, null, null, null);
        var parametros =
                new Parametros(null, null, false, null, null, false, false, false, false, false, false, "Pie", null);
        var snapshot = new Snapshot(
                id,
                (short) 7,
                true,
                "Nota",
                new BigDecimal("123.123456"),
                proyecto,
                parametros,
                new Display(2, 2),
                List.of(new Firmante(id, "Ana", "Revisora", "APROBADO", (short) 1)),
                List.of(capitulo),
                List.of());
        var projection = new DocumentProjectionV1(1, "presupuesto", "xlsx", Map.of(), snapshot);
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(PresupuestoXlsxRenderer.renderizar(projection)))) {
            var sheet = wb.getSheetAt(0);
            assertEquals("Presupuesto", sheet.getSheetName());
            assertEquals("PRESUPUESTO", sheet.getRow(0).getCell(0).getStringCellValue());
            var chapter = sheet.getRow(6);
            assertEquals("Capítulo", chapter.getCell(2).getStringCellValue());
            assertEquals("99.123456", chapter.getCell(6).getRawValue());
            var row = sheet.getRow(7);
            assertEquals("=COD", row.getCell(1).getStringCellValue());
            assertEquals("2.123456", row.getCell(4).getRawValue());
            assertEquals("9.123456", row.getCell(6).getRawValue());
            assertEquals("123.123456", sheet.getRow(8).getCell(6).getRawValue());
            assertEquals(
                    "Nota: los precios no incluyen IVA",
                    sheet.getRow(9).getCell(0).getStringCellValue());
            assertEquals("APROBADO POR:", sheet.getRow(11).getCell(0).getStringCellValue());
            assertNotNull(sheet.getRepeatingRows());
        }
    }
}
