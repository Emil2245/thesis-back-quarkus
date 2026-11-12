package ec.uce.propuestas.documentos.render;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

class PresupuestoPdfRendererTest {
    @Test
    void rendersProjectedBudgetWithOriginalLayout() throws Exception {
        for (boolean horizontal : new boolean[] {false, true}) {
            for (int precision : new int[] {2, 4}) {
                var projection = projection(horizontal, precision);
                byte[] bytes = PresupuestoPdfRenderer.renderizar(projection);
                assertTrue(bytes.length > 0);
                var pdf = new PdfReader(bytes);
                try {
                    int pages = pdf.getNumberOfPages();
                    assertTrue(pages > 1, "Long budget must paginate");
                    var extractor = new PdfTextExtractor(pdf);
                    var all = new StringBuilder();
                    for (int page = 1; page <= pages; page++) {
                        var size = pdf.getPageSize(page);
                        assertEquals(horizontal ? 842 : 595, size.getWidth(), 1);
                        assertEquals(horizontal ? 595 : 842, size.getHeight(), 1);
                        assertEquals(0, pdf.getPageRotation(page));
                        String text = extractor.getTextFromPage(page);
                        all.append(text).append('\n');
                        assertTrue(text.contains("Página " + page + " de " + pages), text);
                        if (text.contains("FILA")) {
                            for (String header : List.of("Código", "Descripción", "P.Unitario", "P.Total")) {
                                assertTrue(text.contains(header), "Page " + page + " missing " + header);
                                assertTrue(text.indexOf(header) < text.indexOf("FILA"));
                            }
                        }
                    }
                    String text = all.toString();
                    for (String marker : List.of(
                            "PRESUPUESTO",
                            "Dirección institucional",
                            "Subdirección",
                            "Proyecto histórico ñ",
                            "2025",
                            "Versión: 7",
                            projection.snapshot().presupuestoId().toString(),
                            "TABLA DE DESCRIPCIÓN DE RUBROS, UNIDADES, CANTIDADES Y PRECIOS",
                            "ñ m² m³",
                            "=COD",
                            "TOTAL",
                            "Nota: los precios no incluyen IVA",
                            "Nota propia",
                            "Legal ñ",
                            "Técnico ñ",
                            "Pie final",
                            precision == 2 ? "4321.99" : "4321.9877",
                            precision == 2 ? "12.35" : "12.3457",
                            precision == 2 ? "98.77" : "98.7654",
                            precision == 2 ? "123.12" : "123.1235")) {
                        assertTrue(text.contains(marker), marker + " missing: " + text);
                    }
                    assertTrue(text.indexOf("Capítulo raíz") < text.indexOf("Subcapítulo"));
                    assertTrue(text.indexOf("Subcapítulo") < text.indexOf("HIJO"));
                    assertTrue(text.indexOf("HIJO") < text.indexOf("PRIMERO"));
                    assertTrue(text.indexOf("PRIMERO") < text.indexOf("DÉCIMO"));
                    assertTrue(text.indexOf("Legal ñ") < text.indexOf("Técnico ñ"));
                    for (int i = 0; i < 60; i++) assertTrue(text.contains("FILA" + i + "FIN"));
                } finally {
                    pdf.close();
                }
            }
        }
    }

    private static DocumentProjectionV1 projection(boolean horizontal, int precision) {
        var id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var rows = new ArrayList<Rubro>();
        rows.add(rubro(id, "1.10", "DÉCIMO"));
        rows.add(rubro(id, "1.2", "PRIMERO"));
        for (int i = 0; i < 60; i++) rows.add(rubro(id, "1." + (i + 20), "FILA" + i + "FIN"));
        var child = new Capitulo(
                id,
                "1.1",
                "Subcapítulo",
                (short) 1,
                decimal("98.7654"),
                List.of(),
                List.of(rubro(id, "1.1.1", "HIJO")));
        var chapter = new Capitulo(id, "1", "Capítulo raíz", (short) 1, decimal("4321.98765"), List.of(child), rows);
        var project = new Proyecto(
                id,
                "Proyecto histórico ñ",
                "P",
                null,
                (short) 2025,
                null,
                null,
                null,
                null,
                "Dirección institucional",
                "Subdirección");
        var parameters = new Parametros(
                null, null, false, null, null, false, false, false, false, false, false, "Pie final", null);
        var snapshot = new Snapshot(
                id,
                (short) 7,
                true,
                "Nota propia",
                decimal("123.12345"),
                project,
                parameters,
                new Display(precision, 2),
                List.of(
                        new Firmante(id, "Legal ñ", "Legal", "APROBADO", (short) 1),
                        new Firmante(id, "Técnico ñ", "Técnico", "REVISADO", (short) 2)),
                List.of(chapter),
                List.of());
        return new DocumentProjectionV1(
                1, "presupuesto", "pdf", Map.of("orientacion", horizontal ? "horizontal" : "vertical"), snapshot);
    }

    private static Rubro rubro(UUID id, String item, String description) {
        return new Rubro(
                id,
                id,
                item,
                "=COD",
                description + " ñ m² m³",
                "m",
                decimal("12.34567"),
                decimal("3.12345"),
                decimal("98.7654"));
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
