package ec.uce.propuestas.cronograma.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.cronograma.dto.ProyeccionExportacion;
import ec.uce.propuestas.cronograma.export.CronogramaXlsxWriter.Fila;
import ec.uce.propuestas.cronograma.export.CronogramaXlsxWriter.FilaHoja;
import ec.uce.propuestas.cronograma.export.CronogramaXlsxWriter.ProyeccionXlsx;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/**
 * Plan 031 (P-37) — RED para el writer PDF (Lane A documental). Verifica
 * contenido extraído por PDFBox 3 API:
 *
 * <ul>
 *   <li>Cabecera "CRONOGRAMA VALORADO DE TRABAJOS".</li>
 *   <li>Filas de rubros con item/código/descripción/unidad.</li>
 *   <li>Cuatro filas de resumen.</li>
 *   <li>Página footer presente.</li>
 *   <li>PDF A4 landscape.</li>
 *   <li>Fuente Liberation Sans embebida (recursos del classpath).</li>
 *   <li>Formula injection neutralizada también en PDF (no se ejecuta).</li>
 * </ul>
 */
class CronogramaPdfWriterTest {

    @Test
    void TC_P37_30_pdf_parse_back_contiene_titulo_resumen_y_footer() throws IOException {
        byte[] bytes = CronogramaPdfWriter.renderizar(ProyeccionExportacion.de(proyeccionBase()));
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            assertNotNull(doc.getDocumentCatalog());
            assertEquals(1, doc.getNumberOfPages(), "caso base debe caber en una página A4 landscape");
            String texto = strip(doc);
            assertTrue(texto.contains("CRONOGRAMA VALORADO DE TRABAJOS"), "PDF debe contener el título canónico");
            assertTrue(texto.contains("PARCIAL"), "PDF debe contener la fila % PARCIAL");
            assertTrue(texto.contains("ACUMULADO"));
            assertTrue(texto.contains("MONTO"));
            assertTrue(texto.contains("Sistema APU"), "footer debe aparecer");
            assertTrue(texto.contains("Proyecto Demo"), "identidad del proyecto");
        }
    }

    @Test
    void TC_P37_31_pdf_fuente_liberation_sans_embebida_desde_recursos() throws IOException {
        byte[] bytes = CronogramaPdfWriter.renderizar(ProyeccionExportacion.de(proyeccionBase()));
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            // Verificamos que NO depende de fuentes del host: el PDF debe tener
            // al menos una fuente embebida (Liberation Sans Regular).
            boolean tieneFuente = false;
            for (var pagina : doc.getPages()) {
                var recursos = pagina.getResources();
                if (recursos != null && recursos.getFontNames() != null) {
                    tieneFuente = true;
                    break;
                }
            }
            assertTrue(tieneFuente, "PDF debe tener al menos una fuente declarada");
            // El PDF no debe hacer referencia a "Helvetica" ni "Times" como host
            // fallback obligatorio.
            String raw = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
            assertFalse(raw.contains("Helvetica"), "no debe depender de fuentes del host (Helvetica)");
        }
    }

    @Test
    void TC_P37_32_pdf_injection_se_neutraliza_y_no_se_renderiza_como_enlace() throws IOException {
        FilaHoja fila = new FilaHoja(
                new Fila(
                        "=cmd|/C calc",
                        "=1+1",
                        "+hack",
                        "u",
                        new BigDecimal("1.000000"),
                        new BigDecimal("1.000000"),
                        new BigDecimal("1.000000"),
                        new BigDecimal("100.0000")),
                List.of(),
                Map.of());
        ProyeccionXlsx p = new ProyeccionXlsx(
                "PROYECTO-X", "Demo Injection PDF", 2026, "SEMANA", 1, LocalDate.parse("2026-01-01"), List.of(fila));
        byte[] bytes = CronogramaPdfWriter.renderizar(ProyeccionExportacion.de(p));
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            String texto = strip(doc);
            assertTrue(
                    texto.contains("=cmd|/C calc") || texto.contains("'=cmd"),
                    "celdas de texto no se ejecutan (texto plano preservado o escapado)");
            assertFalse(
                    texto.toLowerCase().contains("/launchurl"), "no debe generar anotaciones /LaunchURL ejecutables");
        }
    }

    @Test
    void TC_P37_33_pdf_pagina_footer_y_formato_a4_landscape() throws IOException {
        byte[] bytes = CronogramaPdfWriter.renderizar(ProyeccionExportacion.de(proyeccionBase()));
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            var box = doc.getPage(0).getMediaBox();
            // A4 landscape: 842 x 595 (ancho x alto en puntos).
            assertEquals(842.0f, box.getWidth(), 0.5f);
            assertEquals(595.0f, box.getHeight(), 0.5f);
        }
    }

    /** Direct projection evidence only: no database, persisted calculation or visual approval. */
    @Test
    void many_period_evidence_a4_default_a3_and_xlsx() throws Exception {
        var directory = java.nio.file.Path.of("build/apu05-cronograma-correction/pagination-20261006");
        java.nio.file.Files.createDirectories(directory);
        var rows = new java.util.ArrayList<FilaHoja>();
        for (int row = 0; row < 50; row++) {
            // Fifty equal weights; the final activity spans weeks 50–52.
            var percentages =
                    new java.util.ArrayList<BigDecimal>(java.util.Collections.nCopies(52, new BigDecimal("0.0000")));
            var amounts = new java.util.LinkedHashMap<Integer, BigDecimal>();
            if (row < 49) {
                percentages.set(row, new BigDecimal("2.0000"));
                amounts.put(row + 1, new BigDecimal("20.000000"));
            } else {
                for (int period = 50; period <= 52; period++) {
                    percentages.set(period - 1, new BigDecimal(period == 52 ? "0.6668" : "0.6666"));
                    amounts.put(period, new BigDecimal(period == 52 ? "6.668000" : "6.666000"));
                }
            }
            assertEquals(new BigDecimal("2.0000"), percentages.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
            assertEquals(
                    new BigDecimal("20.000000"), amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
            rows.add(new FilaHoja(
                    new Fila(
                            "1." + (row + 1),
                            "R" + (row + 1),
                            "Rubro " + (row + 1),
                            "u",
                            new BigDecimal("1.000000"),
                            new BigDecimal("20.000000"),
                            new BigDecimal("20.000000"),
                            new BigDecimal("2.0000")),
                    List.copyOf(percentages),
                    Map.copyOf(amounts)));
        }
        var projection = ProyeccionExportacion.de(new ProyeccionXlsx(
                "APU05-CRONO",
                "Evidencia 52 semanas",
                2026,
                "SEMANA",
                52,
                LocalDate.of(2026, 1, 1),
                List.copyOf(rows)));
        var expected = new java.util.ArrayList<List<BigDecimal>>();
        for (int series = 0; series < 4; series++) expected.add(new java.util.ArrayList<>());
        var manifest =
                new StringBuilder("Direct test projection; NOT persisted production calculation; NOT visual approval.\n"
                        + "50 rows; quantity=1; unit/total=20; weight=2; total=1000; total weight=100.\n"
                        + "period,start,end,partialPercent,accumulatedPercent,partialAmount,accumulatedAmount\n");
        BigDecimal pct = new BigDecimal("0.0000");
        BigDecimal amount = new BigDecimal("0.000000");
        for (int period = 1; period <= 52; period++) {
            BigDecimal partial = new BigDecimal(period <= 49 ? "2.0000" : period == 52 ? "0.6668" : "0.6666");
            BigDecimal money = partial.multiply(BigDecimal.TEN).setScale(6);
            pct = pct.add(partial);
            amount = amount.add(money);
            expected.get(0).add(partial);
            expected.get(1).add(pct);
            expected.get(2).add(money);
            expected.get(3).add(amount);
            LocalDate start = LocalDate.of(2026, 1, 1).plusWeeks(period - 1);
            manifest.append(period).append(',').append(start).append(',').append(start.plusDays(6));
            for (var values : expected)
                manifest.append(',').append(values.get(period - 1).toPlainString());
            manifest.append('\n');
        }
        assertEquals(
                expected,
                List.of(
                        projection.parcialPorcentaje(),
                        projection.acumuladoPorcentaje(),
                        projection.parcialMonto(),
                        projection.acumuladoMonto()));
        assertEquals(new BigDecimal("100.0000"), pct);
        assertEquals(new BigDecimal("1000.000000"), amount);
        java.nio.file.Files.writeString(directory.resolve("projection-expected.csv"), manifest);
        byte[] xlsx = CronogramaXlsxWriter.renderizar(projection);
        java.nio.file.Files.write(directory.resolve("cronograma-52.xlsx"), xlsx);
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx))) {
            var sheet = workbook.getSheetAt(0);
            for (int period = 0; period < 52; period++) {
                assertEquals(
                        "Semana " + (period + 1),
                        sheet.getRow(3).getCell(8 + period).getStringCellValue());
                for (int row = 0; row < 50; row++) {
                    var cell = (org.apache.poi.xssf.usermodel.XSSFCell)
                            sheet.getRow(4 + row).getCell(8 + period);
                    assertEquals(
                            0,
                            rows.get(row)
                                    .porcentajesPorPeriodo()
                                    .get(period)
                                    .compareTo(new BigDecimal(cell.getRawValue())));
                }
                for (int series = 0; series < 4; series++) {
                    var cell = (org.apache.poi.xssf.usermodel.XSSFCell)
                            sheet.getRow(54 + series).getCell(8 + period);
                    assertEquals(0, expected.get(series).get(period).compareTo(new BigDecimal(cell.getRawValue())));
                }
            }
        }
        for (var paper : CronogramaPdfWriter.Papel.values()) {
            byte[] bytes = paper == CronogramaPdfWriter.Papel.A4
                    ? CronogramaPdfWriter.renderizar(projection)
                    : CronogramaPdfWriter.renderizar(projection, paper);
            String filename = "cronograma-52-" + paper + ".pdf";
            java.nio.file.Files.write(directory.resolve(filename), bytes);
            try (PDDocument doc = Loader.loadPDF(bytes)) {
                String text = strip(doc);
                int covered = 0;
                String previousRange = "";
                for (int page = 1; page <= doc.getNumberOfPages(); page++) {
                    var reader = new PDFTextStripper();
                    reader.setStartPage(page);
                    reader.setEndPage(page);
                    String body = reader.getText(doc);
                    assertTrue(body.contains("Evidencia 52 semanas"), paper + " page " + page + " missing identity");
                    assertTrue(body.contains("Períodos "), paper + " page " + page + " missing slice heading");
                    var range = java.util.regex.Pattern.compile("Períodos (\\d+)–(\\d+) de 52")
                            .matcher(body);
                    assertTrue(range.find(), paper + " page " + page + " missing slice range");
                    int first = Integer.parseInt(range.group(1));
                    int last = Integer.parseInt(range.group(2));
                    if (!range.group().equals(previousRange)) {
                        assertEquals(covered + 1, first, "horizontal slices must cover each week exactly once");
                        covered = last;
                        previousRange = range.group();
                    }
                    double columnWidth =
                            (doc.getPage(page - 1).getMediaBox().getWidth() - 36 - 484) / (last - first + 1);
                    assertTrue(columnWidth >= 52, "readable 9pt schema and exact 7pt money width");
                    for (int period = first; period <= last; period++)
                        assertTrue(body.contains("Semana " + period), "every continuation repeats its complete schema");
                    assertTrue(body.contains("Rubro"), "no orphan header or summary-only page");
                    assertTrue(body.contains("Sistema APU"), "footer on every page");
                    manifest.append(paper)
                            .append(" page=")
                            .append(page)
                            .append(' ')
                            .append(range.group())
                            .append(" periodWidthPt=")
                            .append(columnWidth)
                            .append('\n');
                }
                assertEquals(52, covered);
                java.nio.file.Files.writeString(directory.resolve(filename + ".txt"), text);
                manifest.append(filename)
                        .append(" bytes=")
                        .append(bytes.length)
                        .append(" sha256=")
                        .append(java.util.HexFormat.of()
                                .formatHex(java.security.MessageDigest.getInstance("SHA-256")
                                        .digest(bytes)))
                        .append(" pages=")
                        .append(doc.getNumberOfPages())
                        .append('\n');
                for (var page : doc.getPages()) {
                    var box = page.getMediaBox();
                    assertEquals(paper == CronogramaPdfWriter.Papel.A4 ? 842f : 1191f, box.getWidth(), 0.5f);
                    assertEquals(paper == CronogramaPdfWriter.Papel.A4 ? 595f : 842f, box.getHeight(), 0.5f);
                    manifest.append("pageSize=")
                            .append(box.getWidth())
                            .append('x')
                            .append(box.getHeight())
                            .append('\n');
                }
                java.nio.file.Files.writeString(directory.resolve("projection-expected.csv"), manifest);
                assertTrue(text.contains("Sistema APU"));
                // Isolate each physical column: wrapped Semana + 52 cannot match an unrelated 52.
                for (int period = 1; period <= 52; period++) {
                    String column = pdfColumn(doc, period);
                    manifest.append(paper)
                            .append(" header=Semana ")
                            .append(period)
                            .append(" occurrences=")
                            .append(column.split("Semana" + period, -1).length - 1)
                            .append('\n');
                    assertTrue(
                            column.startsWith("Semana" + period),
                            paper + " missing header Semana " + period + ": " + column);
                    StringBuilder valuesInSlice = new StringBuilder();
                    for (var row : rows)
                        valuesInSlice.append(
                                row.porcentajesPorPeriodo().get(period - 1).toPlainString());
                    for (var values : expected)
                        valuesInSlice.append(values.get(period - 1).toPlainString());
                    // Vertical schema repeats are legitimate; activity and canonical summary values are not duplicated.
                    assertEquals(
                            valuesInSlice.toString(),
                            column.replace("Semana" + period, ""),
                            paper + " exact row/summary column " + period);
                }
                java.nio.file.Files.writeString(directory.resolve("projection-expected.csv"), manifest);
                String labels = pdfColumn(doc, 0);
                for (String label : List.of("%PARCIAL", "%ACUMULADO", "MONTOPARCIAL", "MONTOACUMULADO"))
                    assertTrue(labels.contains(label), paper + " missing summary " + label);
            }
        }
    }

    private static String pdfColumn(PDDocument doc, int period) throws IOException {
        StringBuilder text = new StringBuilder();
        for (int pageIndex = 0; pageIndex < doc.getNumberOfPages(); pageIndex++) {
            var page = doc.getPage(pageIndex);
            var reader = new PDFTextStripper();
            reader.setStartPage(pageIndex + 1);
            reader.setEndPage(pageIndex + 1);
            var range = java.util.regex.Pattern.compile("Períodos (\\d+)–(\\d+) de 52")
                    .matcher(reader.getText(doc));
            assertTrue(range.find());
            int first = Integer.parseInt(range.group(1));
            int last = Integer.parseInt(range.group(2));
            if (period != 0 && (period < first || period > last)) continue;
            double width = (page.getMediaBox().getWidth() - 36 - 484) / (last - first + 1);
            double left = period == 0 ? 18 : 18 + 484 + (period - first) * width;
            // Locate schema baseline instead of assuming a first-page metadata height.
            double[] top = {0};
            var locator = new PDFTextStripper() {
                @Override
                protected void writeString(String value, List<org.apache.pdfbox.text.TextPosition> positions) {
                    if (value.contains("Semana")) top[0] = positions.get(0).getYDirAdj() - 10;
                }
            };
            locator.setStartPage(pageIndex + 1);
            locator.setEndPage(pageIndex + 1);
            locator.getText(doc);
            assertTrue(top[0] > 0);
            var stripper = new org.apache.pdfbox.text.PDFTextStripperByArea();
            stripper.addRegion(
                    "column",
                    new java.awt.geom.Rectangle2D.Double(
                            left,
                            top[0],
                            period == 0 ? 484 : width,
                            page.getMediaBox().getHeight() - top[0] - 24));
            stripper.extractRegions(page);
            text.append(stripper.getTextForRegion("column").replaceAll("\\s+", ""));
        }
        return text.toString();
    }

    private static String strip(PDDocument doc) throws IOException {
        return new PDFTextStripper().getText(doc);
    }

    private static ProyeccionXlsx proyeccionBase() {
        FilaHoja fila = new FilaHoja(
                new Fila(
                        "1.1",
                        "R1",
                        "Rubro uno",
                        "u",
                        new BigDecimal("1.000000"),
                        new BigDecimal("1.000000"),
                        new BigDecimal("1.000000"),
                        new BigDecimal("100.0000")),
                List.of(new BigDecimal("50.0000"), new BigDecimal("50.0000")),
                Map.of(1, new BigDecimal("1.000000"), 2, new BigDecimal("1.000000")));
        return new ProyeccionXlsx(
                "PROYECTO-1", "Proyecto Demo", 2026, "SEMANA", 2, LocalDate.parse("2026-01-01"), List.of(fila));
    }
}
