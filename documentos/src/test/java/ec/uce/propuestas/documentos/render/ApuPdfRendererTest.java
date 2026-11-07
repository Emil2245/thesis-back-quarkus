package ec.uce.propuestas.documentos.render;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

class ApuPdfRendererTest {
    @Test
    void rendersReconciledRowsAndSeparateA4IdentifierBands() throws Exception {
        var firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var staleCost = new BigDecimal("999.00");
        var staleHourly = new BigDecimal("888.00");
        var rowCost = new BigDecimal("12.345678");
        var hourly = new BigDecimal("10.123456");
        var detail = new Detalle(
                firstId,
                (short) 1,
                false,
                "Excavadora",
                "h",
                BigDecimal.ONE,
                BigDecimal.ONE,
                null,
                null,
                BigDecimal.TEN,
                staleHourly,
                staleCost,
                null);
        var block = new ApuBlock(
                SeccionTipo.EQUIPO,
                "EQUIPOS",
                (short) 1,
                List.of(new ApuRow(detail, "Excavadora", null, rowCost, hourly)),
                rowCost,
                true);
        var footer = new ApuFooter(List.of(), BigDecimal.ZERO, rowCost, BigDecimal.ZERO, rowCost, rowCost, null);
        var first = new ApuView(firstId, "A-1", "A-1", "Excavación", "m³", null, List.of(block), footer);
        var second = new ApuView(secondId, "A-2", "A-2", "Movimiento", "m³", null, List.of(block), footer);
        var calculated = new CalculatedApu("A-1", List.of(), null, null, null, null, null, null, null, null);
        var persisted = new Apu(
                firstId,
                "A-1",
                "Excavación",
                "m³",
                null,
                null,
                null,
                null,
                null,
                calculated,
                List.of(new Seccion(SeccionTipo.EQUIPO, (short) 1, staleCost, List.of(detail))));
        var project = new Proyecto(firstId, "Proyecto", "P", null, (short) 2025, null, null, null, null, null, null);
        var parameters =
                new Parametros(null, null, false, null, null, false, false, true, false, false, false, null, null);
        var snapshot = new Snapshot(
                firstId,
                (short) 1,
                true,
                null,
                rowCost,
                project,
                parameters,
                new Display(2, 2),
                List.of(),
                List.of(),
                List.of(persisted),
                List.of(first, second));
        var projection = new DocumentProjectionV1(1, "apus", "pdf", Map.of(), snapshot);

        var pdf = new PdfReader(ApuPdfRenderer.renderizar(projection));
        try {
            assertEquals(2, pdf.getNumberOfPages());
            var extractor = new PdfTextExtractor(pdf);
            for (int page = 1; page <= 2; page++) {
                var size = pdf.getPageSize(page);
                assertEquals(595, size.getWidth(), 0.1);
                assertEquals(842, size.getHeight(), 0.1);
                assertEquals(0, pdf.getPageRotation(page));
                String contents = extractor.getTextFromPage(page);
                String ownId = (page == 1 ? firstId : secondId).toString();
                String otherId = (page == 1 ? secondId : firstId).toString();
                assertTrue(contents.contains("APU " + ownId), contents);
                assertFalse(contents.contains(otherId), contents);
                assertTrue(contents.contains("Código: A-" + page), contents);
                assertTrue(contents.contains("ANÁLISIS DE PRECIOS UNITARIOS"), contents);
                assertTrue(contents.contains("Excavadora"), contents);
                assertTrue(contents.contains("10.12"), contents);
                assertTrue(contents.contains("12.35"), contents);
                assertFalse(contents.contains("888.00"), contents);
                assertFalse(contents.contains("999.00"), contents);
                assertTrue(contents.contains("Página " + page + " de 2"), contents);
            }
        } finally {
            pdf.close();
        }
    }
}
