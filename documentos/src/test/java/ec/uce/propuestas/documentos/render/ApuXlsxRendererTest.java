package ec.uce.propuestas.documentos.render;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.*;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ApuXlsxRendererTest {
    @Test
    void rendersEffectiveProjectedCostInBothLayouts() throws Exception {
        var id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var staleCost = new BigDecimal("999.000000");
        var effectiveCost = new BigDecimal("12.345678");
        var detail = new Detalle(
                id,
                (short) 1,
                false,
                "Excavadora",
                "h",
                BigDecimal.ONE,
                BigDecimal.ONE,
                null,
                null,
                BigDecimal.TEN,
                new BigDecimal("888.000000"),
                staleCost,
                null);
        var block = new ApuBlock(
                SeccionTipo.EQUIPO,
                "EQUIPOS",
                (short) 1,
                List.of(new ApuRow(detail, "Excavadora", null, effectiveCost, new BigDecimal("10.123456"))),
                effectiveCost,
                true);
        var footer = new ApuFooter(
                List.of(), BigDecimal.ZERO, effectiveCost, BigDecimal.ZERO, effectiveCost, effectiveCost, null);
        var first = new ApuView(id, "A-1", "A-1", "Excavación", "m3", null, List.of(block), footer);
        var second = new ApuView(secondId, "A-2", "A-2", "Movimiento", "m3", null, List.of(block), footer);
        var calculated = new CalculatedApu("A-1", List.of(), null, null, null, null, null, null, null, null);
        var persisted = new Apu(
                id,
                "A-1",
                "Excavación",
                "m3",
                null,
                null,
                null,
                null,
                null,
                calculated,
                List.of(new Seccion(SeccionTipo.EQUIPO, (short) 1, staleCost, List.of(detail))));
        var project = new Proyecto(id, "Proyecto", "P", null, (short) 2025, null, null, null, null, null, null);
        var parameters =
                new Parametros(null, null, false, null, null, false, false, true, false, false, false, null, null);
        var snapshot = new Snapshot(
                id,
                (short) 1,
                true,
                null,
                effectiveCost,
                project,
                parameters,
                new Display(2, 2),
                List.of(),
                List.of(),
                List.of(persisted),
                List.of(first, second));

        for (String layout : List.of("pestanas", "apilado")) {
            var projection = new DocumentProjectionV1(1, "apus", "xlsx", Map.of("layout", layout), snapshot);
            try (var wb = new XSSFWorkbook(new ByteArrayInputStream(ApuXlsxRenderer.renderizar(projection)))) {
                assertEquals(layout.equals("pestanas") ? 2 : 1, wb.getNumberOfSheets());
                assertEquals(layout.equals("pestanas") ? "A-1" : "APUs", wb.getSheetName(0));
                if (layout.equals("pestanas")) assertEquals("A-2", wb.getSheetName(1));
                int details = 0;
                for (var sheet : wb) {
                    assertEquals(1, sheet.getPrintSetup().getFitWidth());
                    assertEquals(0, sheet.getPrintSetup().getFitHeight());
                    for (var row : sheet) {
                        var label = row.getCell(0);
                        if (label == null
                                || label.getCellType() != CellType.STRING
                                || !label.getStringCellValue().equals("Excavadora")) continue;
                        details++;
                        var hourly = (org.apache.poi.xssf.usermodel.XSSFCell) row.getCell(3);
                        var cost = (org.apache.poi.xssf.usermodel.XSSFCell) row.getCell(5);
                        assertEquals("10.123456", hourly.getRawValue());
                        assertEquals(effectiveCost.toPlainString(), cost.getRawValue());
                        assertNotEquals(staleCost.toPlainString(), cost.getRawValue());
                    }
                }
                assertEquals(2, details);
            }
        }
    }
}
