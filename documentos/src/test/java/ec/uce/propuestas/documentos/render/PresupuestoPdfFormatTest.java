package ec.uce.propuestas.documentos.render;

import static ec.uce.propuestas.documentos.render.Projections.id;
import static ec.uce.propuestas.documentos.render.Projections.load;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Budget PDF format coverage ported in stage 05 from Core's retired {@code PresupuestoPdfWriterTest} (same
 * fixtures, same expected values). Characterization tests: the renderer already exists.
 */
class PresupuestoPdfFormatTest {
    private static final List<String> HEADERS =
            List.of("Ítem", "Código", "Descripción", "Unidad", "Cantidad", "P.Unitario", "P.Total");

    private static byte[] render(String fixture) {
        return PresupuestoPdfRenderer.renderizar(load(fixture));
    }

    @Test
    void producesPdf() throws Exception {
        byte[] bytes = render("presupuesto-p2-pdf-vertical");
        assertTrue(bytes.length > 0);
        try (var pdf = Loader.loadPDF(bytes)) {
            assertTrue(pdf.getNumberOfPages() > 0);
        }
    }

    /** L4: budget UUID, all codes and signers, display precision, order, embedded fonts, no annotations. */
    @Test
    void orientationsFontsValuesAndOrder() throws Exception {
        for (boolean horizontal : new boolean[] {false, true}) {
            for (int precision : new int[] {2, 4}) {
                String fixture = "presupuesto-p" + precision + "-pdf-" + (horizontal ? "horizontal" : "vertical");
                try (var pdf = Loader.loadPDF(render(fixture))) {
                    var box = pdf.getPage(0).getMediaBox();
                    assertEquals(horizontal ? 842 : 595, box.getWidth(), 1);
                    assertEquals(horizontal ? 595 : 842, box.getHeight(), 1);
                    assertEquals(0, pdf.getPage(0).getRotation());
                    var stripper = new PDFTextStripper();
                    stripper.setStartPage(1);
                    stripper.setEndPage(1);
                    assertTrue(stripper.getText(pdf).contains(id(30).toString()), "budget identity on first page");
                    String text = new PDFTextStripper().getText(pdf);
                    for (String expected : new String[] {
                        "Dirección institucional",
                        "Subdirección",
                        "Proyecto histórico ñ",
                        "2025",
                        "Versión: 7",
                        id(30).toString(),
                        "ñ m² m³",
                        "=SUM(A1:A2)",
                        "+SEGUNDO",
                        "@PRIMERO",
                        "-HIJO",
                        "Nota: los precios no incluyen IVA",
                        "TABLA DE DESCRIPCIÓN DE RUBROS, UNIDADES, CANTIDADES Y PRECIOS",
                        "PROYECTO: Proyecto histórico ñ",
                        "Legal ñ",
                        "Técnico ñ",
                        precision == 2 ? "4321.99" : "4321.9877",
                        precision == 2 ? "12.35" : "12.3457",
                        precision == 2 ? "98.77" : "98.7654",
                        precision == 2 ? "888.89" : "888.8889",
                        precision == 2 ? "123.12" : "123.1235",
                        precision == 2 ? "777.78" : "777.7778"
                    }) assertTrue(text.contains(expected), fixture + ": " + expected + " missing: " + text);
                    assertTrue(text.indexOf("Capítulo raíz") < text.indexOf("Subcapítulo"));
                    assertTrue(text.indexOf("-HIJO") < text.indexOf("@PRIMERO"));
                    assertTrue(text.indexOf("@PRIMERO") < text.indexOf("+SEGUNDO"));
                    assertTrue(text.indexOf("+SEGUNDO") < text.indexOf("DÉCIMO"));
                    assertTrue(text.indexOf("Legal ñ") < text.indexOf("Técnico ñ"));
                    assertFalse(text.contains("999999"));
                    assertFalse(text.contains("888888"));
                    for (var page : pdf.getPages()) {
                        assertTrue(page.getAnnotations().isEmpty());
                        int fonts = 0;
                        for (var name : page.getResources().getFontNames()) {
                            assertTrue(page.getResources().getFont(name).isEmbedded());
                            fonts++;
                        }
                        assertTrue(fonts > 0);
                    }
                }
            }
        }
    }

    @Test
    void signersPrintInSpecOrder() throws Exception {
        try (var pdf = Loader.loadPDF(render("presupuesto-firmantes-pdf"))) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.indexOf("CONSOLIDADO POR:") < text.indexOf("Carla"), text);
            assertTrue(text.indexOf("Carla") < text.indexOf("APROBADO POR:"), text);
            assertTrue(text.indexOf("Beto Uno") < text.indexOf("Ana Dos"), text);
        }
    }

    @Test
    void continuesWithoutCroppingRepeatingHeadersAndPagination() throws Exception {
        try (var pdf = Loader.loadPDF(render("presupuesto-p2-x140-pdf-vertical"))) {
            int pages = pdf.getNumberOfPages();
            assertTrue(pages > 2);
            var stripper = new PDFTextStripper();
            String all = stripper.getText(pdf);
            for (int i = 0; i < 140; i++) assertTrue(all.contains("FIN" + i), "FIN" + i);
            for (int p = 1; p <= pages; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(pdf);
                assertTrue(text.contains("Página " + p + " de " + pages), text);
                if (text.contains("LARGO")) {
                    assertTrue(text.contains("Código"));
                    assertTrue(text.contains("Descripción"));
                    assertTrue(text.contains("P.Unitario"));
                    assertTrue(text.contains("P.Total"));
                }
            }
        }
    }

    /** L4: every numeric XLSX cell, formatted at display precision, appears in the PDF of the same capture. */
    @Test
    void omitsMissingInstitutionAndKeepsVisibleParityWithXlsx() throws Exception {
        try (var pdf = Loader.loadPDF(render("presupuesto-sin-institucion-pdf"))) {
            String text = new PDFTextStripper().getText(pdf);
            assertFalse(text.contains("Dirección institucional"));
            assertFalse(text.contains("Subdirección"));
            assertTrue(text.contains("Proyecto histórico ñ"));
        }
        for (int precision : new int[] {2, 4}) {
            try (var pdf = Loader.loadPDF(render("presupuesto-p" + precision + "-pdf-vertical"));
                    var wb = new XSSFWorkbook(new ByteArrayInputStream(
                            PresupuestoXlsxFormatTest.render("presupuesto-p" + precision + "-xlsx")))) {
                String text = new PDFTextStripper().getText(pdf);
                var format = new DataFormatter(Locale.US);
                for (var row : wb.getSheetAt(0))
                    for (var cell : row)
                        if (cell.getCellType() == CellType.NUMERIC)
                            assertTrue(
                                    text.contains(format.formatCellValue(cell)),
                                    cell.getAddress().toString());
            }
        }
    }

    @Test
    void oversizedSingleRowNeverLosesText() throws Exception {
        for (boolean horizontal : new boolean[] {false, true}) {
            try (var pdf =
                    Loader.loadPDF(render("presupuesto-extremo-p2-pdf-" + (horizontal ? "horizontal" : "vertical")))) {
                String text = new PDFTextStripper().getText(pdf);
                assertTrue(pdf.getNumberOfPages() > 1);
                assertTrue(text.contains("INICIOGIGANTE"));
                assertTrue(text.contains("FINGIGANTE"));
                assertEquals(650, text.split("descripción", -1).length - 1, "every wrapped segment survives");
                assertTrue(text.replaceAll("\\s+", "").contains("0001234567890123456789012345678901234567890"));
                var perPage = new PDFTextStripper();
                for (int p = 1; p <= pdf.getNumberOfPages(); p++) {
                    perPage.setStartPage(p);
                    perPage.setEndPage(p);
                    String pageText = perPage.getText(pdf);
                    if (pageText.contains("descripción")) {
                        for (String header : HEADERS) assertTrue(pageText.contains(header), "page " + p + " " + header);
                        assertTrue(pageText.indexOf("Descripción") < pageText.indexOf("descripción"));
                        if (p > 1) {
                            var area = new PDFTextStripperByArea();
                            var page = pdf.getPage(p - 1);
                            area.addRegion(
                                    "header",
                                    new Rectangle2D.Float(
                                            24, 20, page.getMediaBox().getWidth() - 48, 40));
                            area.extractRegions(page);
                            String top = area.getTextForRegion("header");
                            for (String header : HEADERS)
                                assertTrue(
                                        top.contains(header), "top header on continuation page " + p + ": " + header);
                        }
                    }
                }
                assertTrue(text.contains("-2.35"));
                assertTrue(text.contains("-2.36"));
            }
        }
    }

    @Test
    void precisionZeroRoundsNegativeTiesHalfUp() throws Exception {
        try (var pdf = Loader.loadPDF(render("presupuesto-extremo-p0-pdf-horizontal"))) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.matches("(?s).*\\b2 -2\\b.*"), text);
            assertFalse(text.contains("-1.5"));
            assertFalse(text.contains("2.345"));
            assertFalse(text.contains(Character.toString(1)));
        }
    }
}
