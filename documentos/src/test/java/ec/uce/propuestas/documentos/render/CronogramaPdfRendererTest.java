package ec.uce.propuestas.documentos.render;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1;
import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

class CronogramaPdfRendererTest {
    @Test
    void rendersCapturedValuesAndLandscapePaper() throws Exception {
        for (String paper : List.of("a4", "a3")) {
            var pdf = new PdfReader(CronogramaPdfRenderer.renderizar(projection("pdf", paper)));
            try {
                assertEquals(paper.equals("a4") ? 842 : 1191, pdf.getPageSize(1).getWidth(), 1);
                assertEquals(paper.equals("a4") ? 595 : 842, pdf.getPageSize(1).getHeight(), 1);
                String text = new PdfTextExtractor(pdf).getTextFromPage(1);
                for (String value : List.of(
                        "Cap. 1",
                        "Obra",
                        "12.35",
                        "77.7778",
                        "91.2346",
                        "123.46",
                        "% PARCIAL",
                        "% ACUMULADO",
                        "MONTO PARCIAL",
                        "MONTO ACUMULADO",
                        "Sistema APU")) assertTrue(text.contains(value), value + " missing: " + text);
                assertTrue(text.indexOf("Cap. 1") < text.indexOf("Obra"));
            } finally {
                pdf.close();
            }
        }
    }

    @Test
    void rejectsNonPdf() {
        assertThrows(IllegalArgumentException.class, () -> CronogramaPdfRenderer.renderizar(projection("xlsx", null)));
    }

    private static CronogramaProjectionV1 projection(String format, String paper) {
        var row = new SheetRow(
                "1.1",
                "C",
                "Obra",
                "m",
                "12.345",
                "3",
                "123.456",
                "77.77775",
                List.of("91.23455"),
                List.of(new PeriodAmount(1, "4")));
        var totals = new Totals(List.of("91.23455"), List.of("91.23455"), List.of("123.456"), List.of("123.456"));
        var sheet = new Sheet(
                "P",
                "Proyecto",
                2025,
                "MES",
                1,
                null,
                "Institución",
                "",
                2,
                4,
                List.of(row),
                List.of(new Chapter("1", "Capítulo", "123.456", 0)),
                totals);
        return new CronogramaProjectionV1(1, format, paper, sheet, null);
    }
}
