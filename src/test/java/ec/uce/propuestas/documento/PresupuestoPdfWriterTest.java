package ec.uce.propuestas.documento;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class PresupuestoPdfWriterTest {
    @Test
    void producePdf() throws Exception {
        byte[] bytes = render(2, 0, false);
        assertTrue(bytes.length > 0, "Expected budget PDF bytes; actual empty writer output");
        try (var pdf = Loader.loadPDF(bytes)) {
            assertTrue(pdf.getNumberOfPages() > 0);
        }
    }

    @Test
    void orientacionesFuenteValoresYOrden() throws Exception {
        for (boolean horizontal : new boolean[] {false, true}) {
            for (int precision : new int[] {2, 4}) {
                try (var pdf = Loader.loadPDF(render(precision, 0, horizontal))) {
                    var box = pdf.getPage(0).getMediaBox();
                    assertEquals(horizontal ? 842 : 595, box.getWidth(), 1);
                    assertEquals(horizontal ? 595 : 842, box.getHeight(), 1);
                    assertEquals(0, pdf.getPage(0).getRotation());
                    var stripper = new PDFTextStripper();
                    String text = stripper.getText(pdf);
                    for (String expected : new String[] {
                        "Dirección institucional",
                        "Subdirección",
                        "Proyecto histórico ñ",
                        "2025",
                        "Versión: 7",
                        PresupuestoDocumentoFixture.id(30).toString(),
                        "ñ m² m³",
                        "=SUM(A1:A2)",
                        "+SEGUNDO",
                        "@PRIMERO",
                        "-HIJO",
                        "sin IVA",
                        "Legal ñ",
                        "Técnico ñ",
                        precision == 2 ? "4321.99" : "4321.9877",
                        precision == 2 ? "12.35" : "12.3457",
                        precision == 2 ? "98.77" : "98.7654",
                        precision == 2 ? "888.89" : "888.8889",
                        precision == 2 ? "123.12" : "123.1235",
                        precision == 2 ? "777.78" : "777.7778"
                    }) assertTrue(text.contains(expected), expected + " missing: " + text);
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
    void continuaSinRecortarYRepiteCabecerasYPaginacion() throws Exception {
        try (var pdf = Loader.loadPDF(render(2, 140, false))) {
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

    @Test
    void omiteInstitucionAusenteYConservaParidadVisible() throws Exception {
        var s = PresupuestoDocumentoFixture.sinInstitucion();
        try (var pdf = Loader.loadPDF(
                PresupuestoPdfWriter.renderizar(s, PresupuestoDocumentoFixture.opciones("pdf", false)))) {
            String text = new PDFTextStripper().getText(pdf);
            assertFalse(text.contains("Dirección institucional"));
            assertFalse(text.contains("Subdirección"));
            assertTrue(text.contains("Proyecto histórico ñ"));
        }
        for (int precision : new int[] {2, 4}) {
            try (var pdf = Loader.loadPDF(render(precision, 0, false));
                    var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                            new java.io.ByteArrayInputStream(PresupuestoXlsxWriterTest.render(precision, 0)))) {
                String text = new PDFTextStripper().getText(pdf);
                var format = new org.apache.poi.ss.usermodel.DataFormatter(java.util.Locale.US);
                for (var row : wb.getSheetAt(0))
                    for (var cell : row)
                        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC)
                            assertTrue(
                                    text.contains(format.formatCellValue(cell)),
                                    cell.getAddress().toString());
            }
        }
    }

    @Test
    void unaFilaMayorQuePaginaNoPierdeTexto() throws Exception {
        var directory = java.nio.file.Path.of("build/bud03-evidence");
        java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.write(directory.resolve("hostile-multipage-landscape.pdf"), render(2, 140, true));
        for (boolean horizontal : new boolean[] {false, true}) {
            byte[] bytes = PresupuestoPdfWriter.renderizar(
                    PresupuestoDocumentoFixture.extremo(2), PresupuestoDocumentoFixture.opciones("pdf", horizontal));
            java.nio.file.Files.write(
                    directory.resolve("extreme-" + (horizontal ? "landscape" : "portrait") + ".pdf"), bytes);
        }
        for (boolean horizontal : new boolean[] {false, true}) {
            try (var pdf = Loader.loadPDF(java.nio.file.Files.readAllBytes(
                    directory.resolve("extreme-" + (horizontal ? "landscape" : "portrait") + ".pdf")))) {
                String text = new PDFTextStripper().getText(pdf);
                assertTrue(pdf.getNumberOfPages() > 1);
                assertTrue(text.contains("INICIOGIGANTE"));
                assertTrue(text.contains("FINGIGANTE"), "Oversized single row must preserve its final marker");
                assertEquals(650, text.split("descripción", -1).length - 1, "Every wrapped segment must survive");
                assertTrue(
                        text.replaceAll("\\s+", "").contains("0001234567890123456789012345678901234567890"),
                        "Long numeric code survives wrapping");
                var perPage = new PDFTextStripper();
                for (int p = 1; p <= pdf.getNumberOfPages(); p++) {
                    perPage.setStartPage(p);
                    perPage.setEndPage(p);
                    String pageText = perPage.getText(pdf);
                    if (pageText.contains("descripción")) {
                        for (String header : PresupuestoXlsxWriter.CABECERAS)
                            assertTrue(pageText.contains(header), "Page " + p + " missing " + header);
                        assertTrue(
                                pageText.indexOf("Descripción") < pageText.indexOf("descripción"),
                                "Header precedes continuation body");
                        if (p > 1) {
                            var area = new org.apache.pdfbox.text.PDFTextStripperByArea();
                            var page = pdf.getPage(p - 1);
                            area.addRegion(
                                    "header",
                                    new java.awt.geom.Rectangle2D.Float(
                                            24, 20, page.getMediaBox().getWidth() - 48, 40));
                            area.extractRegions(page);
                            String top = area.getTextForRegion("header");
                            for (String header : PresupuestoXlsxWriter.CABECERAS)
                                assertTrue(
                                        top.contains(header), "Top header on continuation page " + p + ": " + header);
                        }
                    }
                }
                assertTrue(text.contains("-2.35"));
                assertTrue(text.contains("-2.36"));
            }
        }
    }

    @Test
    void precisionCeroRedondeaEmpatesNegativosHalfUp() throws Exception {
        var snapshot = PresupuestoDocumentoFixture.extremo(0);
        byte[] bytes = PresupuestoPdfWriter.renderizar(snapshot, PresupuestoDocumentoFixture.opciones("pdf", true));
        var directory = java.nio.file.Path.of("build/bud03-evidence");
        java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.write(directory.resolve("extreme-p0-landscape.pdf"), bytes);
        try (var pdf = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(
                    text.matches("(?s).*\\b2 -2\\b.*"),
                    "Negative -1.5 quantity and positive 2.345 PU round HALF_UP at precision zero: " + text);
            assertFalse(text.contains("-1.5"));
            assertFalse(text.contains("2.345"));
            assertFalse(text.contains(Character.toString(1)));
        }
    }

    private static byte[] render(int precision, int extras, boolean horizontal) {
        return PresupuestoPdfWriter.renderizar(
                PresupuestoDocumentoFixture.snapshot(precision, extras),
                PresupuestoDocumentoFixture.opciones("pdf", horizontal));
    }
}
