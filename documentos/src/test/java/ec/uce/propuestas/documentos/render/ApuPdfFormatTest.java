package ec.uce.propuestas.documentos.render;

import static ec.uce.propuestas.documentos.render.ApuFixtures.as;
import static ec.uce.propuestas.documentos.render.ApuFixtures.copy;
import static ec.uce.propuestas.documentos.render.ApuFixtures.pdf;
import static ec.uce.propuestas.documentos.render.ApuFixtures.row;
import static ec.uce.propuestas.documentos.render.Projections.load;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.ApuBlock;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.ApuFooter;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.ApuRow;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.ApuView;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.Detalle;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.Display;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.Firmante;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.Proyecto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

/**
 * APU PDF format coverage ported in stage 05 from Core's retired {@code ApuPdfWriterTest},
 * {@code ApuIdentidadFirmasTest} (CHK-26/27) and the PDF identity/footer checks of {@code ApuDocumentoEvidenceIT}.
 * Same fixtures through the real Core mapper, same expected values. Characterization tests.
 */
class ApuPdfFormatTest {

    private static byte[] render(DocumentProjectionV1 projection) {
        return ApuPdfRenderer.renderizar(pdf(projection));
    }

    /** Port of the old {@code documento(activos, money, pct)}: renamed codes, hostile description, display. */
    private static DocumentProjectionV1 documento(boolean activos, int money, int pct) {
        var source = load(activos ? "apus-activos-pdf" : "apus-inactivos-pdf");
        var views = source.snapshot().apuRender();
        var apus = new ArrayList<ApuView>();
        for (int i = 0; i < views.size(); i++) {
            var a = views.get(i);
            apus.add(copy(
                    a,
                    "COD" + i,
                    (activos ? (i + 1) + " " : "") + "COD" + i,
                    "ñ m² m³ á é í ó ú ü ¿ ¡ Ω =<script> @SUM(A1) https://example.org",
                    a.bloques(),
                    a.pie()));
        }
        return ApuFixtures.with(source, null, new Display(money, pct), null, apus);
    }

    private static String page(PDDocument pdf, int page) throws Exception {
        var stripper = new PDFTextStripper();
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        return stripper.getText(pdf);
    }

    private static int occurrences(String text, String value) {
        return text.split(Pattern.quote(value), -1).length - 1;
    }

    private static void geometryAndSafety(PDDocument pdf) throws Exception {
        assertNull(pdf.getDocumentCatalog().getOpenAction());
        assertFalse(pdf.isEncrypted());
        for (var page : pdf.getPages()) {
            assertEquals(595, page.getMediaBox().getWidth(), 0.1);
            assertEquals(842, page.getMediaBox().getHeight(), 0.1);
            assertEquals(0, page.getRotation());
            assertTrue(page.getAnnotations().isEmpty());
            int fonts = 0;
            for (var name : page.getResources().getFontNames()) {
                var font = page.getResources().getFont(name);
                assertTrue(font.isEmbedded());
                assertNotNull(font.getFontDescriptor().getFontFile2());
                fonts++;
            }
            assertTrue(fonts > 0);
        }
        new PDFTextStripper() {
            @Override
            protected void processTextPosition(TextPosition position) {
                float y = position.getYDirAdj();
                assertTrue(
                        Math.abs(y - 24) < 0.2
                                || Math.abs(y - 38) < 0.2
                                || (y >= 62 && y <= 802)
                                || Math.abs(y - 822) < 0.2,
                        "text in identifier, body or pagination band: " + position.getUnicode() + " y=" + y);
                assertTrue(y >= 15 && y <= 825, position.getUnicode() + " y=" + y);
                assertTrue(
                        position.getXDirAdj() >= 23 && position.getXDirAdj() + position.getWidthDirAdj() <= 573,
                        position.getUnicode() + " x=" + position.getXDirAdj());
                super.processTextPosition(position);
            }
        }.getText(pdf);
    }

    @Test
    void shortA4StartsNewPagePerIdentityWithEmbeddedInertFont() throws Exception {
        var d = documento(true, 2, 4);
        var apus = d.snapshot().apuRender();
        try (var pdf = Loader.loadPDF(render(d))) {
            assertEquals(2, pdf.getNumberOfPages());
            for (int i = 0; i < 2; i++) {
                String text = page(pdf, i + 1);
                assertTrue(text.contains(apus.get(i).id().toString()));
                assertTrue(text.contains("Código: " + (i + 1) + " COD" + i));
                assertTrue(text.contains("ANÁLISIS DE PRECIOS UNITARIOS"));
                assertTrue(text.contains("ñ m² m³ á é í ó ú ü ¿ ¡ Ω"));
                assertTrue(text.contains("=<script> @SUM(A1) https://example.org"));
                assertTrue(text.contains("Página " + (i + 1) + " de 2"));
                assertFalse(text.contains(apus.get(1 - i).id().toString()));
            }
            geometryAndSafety(pdf);
        }
    }

    @Test
    void persistedAmountsHmOrderAndIndependentPrecision() throws Exception {
        try (var pdf = Loader.loadPDF(render(documento(true, 2, 4)))) {
            String text = page(pdf, 1);
            for (String value : List.of(
                    "33.12",
                    "4.99",
                    "11.12",
                    "22.00",
                    "12.00",
                    "9.00",
                    "10.00",
                    "2.000001",
                    "0.000123456",
                    "18.0000%",
                    "7.3000%MO")) assertTrue(text.contains(value), value + " missing: " + text);
            assertTrue(text.indexOf("Descripción") < text.indexOf("Herramienta Menor"));
            assertTrue(text.indexOf("@SUM(A1)") < text.indexOf("Herramienta Menor"));
            assertTrue(text.indexOf("EQUIPOS (M)") < text.indexOf("MANO DE OBRA (N)"));
            assertTrue(text.indexOf("MANO DE OBRA (N)") < text.indexOf("MATERIALES (O)"));
            assertTrue(text.indexOf("MATERIALES (O)") < text.indexOf("TRANSPORTE (P)"));
            assertEquals(2, occurrences(text, "Subtotal EQUIPOS (M)"));
            assertTrue(text.contains("Costo total 123.12"));
            assertTrue(text.contains("Valor ofertado 123.12"));
            assertTrue(text.indexOf("Legal ñ") < text.indexOf("Técnico ñ"));
            assertFalse(text.contains("999999"));
            assertFalse(text.contains("Costo directo ajustado"));
        }
    }

    @Test
    void absentTogglesInventNoInstitutionNorSigners() throws Exception {
        var original = documento(false, 2, 1);
        var p = original.snapshot().proyecto();
        var proyecto = new Proyecto(
                p.id(),
                p.nombre(),
                p.codigo(),
                p.descripcion(),
                p.anio(),
                p.fechaInicio(),
                p.plazoEjecucion(),
                p.plazoUnidad(),
                p.estado(),
                null,
                null);
        var d = ApuFixtures.with(original, proyecto, null, List.of(), null);
        try (var pdf = Loader.loadPDF(render(d))) {
            String text = new PDFTextStripper().getText(pdf);
            for (String absent : List.of(
                    "Proyecto:",
                    "Subtotal",
                    "MANO DE OBRA",
                    "TRANSPORTE",
                    "Legal ñ",
                    "Técnico ñ",
                    "Dirección institucional",
                    "(M)",
                    "Código: 1")) assertFalse(text.contains(absent), absent);
            assertTrue(text.contains("EQUIPOS"));
            assertTrue(text.contains("MATERIALES"));
            assertTrue(text.contains("18.0%"));
        }
    }

    @Test
    void halfUpZeroAndTwoNegativesWithoutRoundingQuantityOrYield() throws Exception {
        for (int precision : new int[] {0, 2}) {
            var d = documento(true, precision, 3);
            var a = d.snapshot().apuRender().getFirst();
            var pie = new ApuFooter(
                    a.pie().subtotales(),
                    new BigDecimal("0.123456"),
                    new BigDecimal("2.345"),
                    new BigDecimal("-2.355"),
                    new BigDecimal("-1.5"),
                    new BigDecimal("2.5"),
                    "FINPIE");
            d = ApuFixtures.apus(d, List.of(copy(a, a.codigo(), a.descripcion(), a.bloques(), pie)));
            try (var pdf = Loader.loadPDF(render(d))) {
                String text = new PDFTextStripper().getText(pdf);
                assertTrue(text.contains(precision == 0 ? "Costo directo 2" : "2.35"), text);
                assertTrue(text.contains(precision == 0 ? "-2" : "-2.36"), text);
                assertTrue(text.contains(precision == 0 ? "Valor ofertado 3" : "2.50"), text);
                assertTrue(text.contains("12.346%"));
                assertTrue(text.contains("2.000001"));
                assertTrue(text.contains("0.000123456"));
            }
        }
    }

    @Test
    void longApuContinuesSectionWithoutDuplicatingAmountsOrBlankPages() throws Exception {
        var d = documento(true, 2, 4);
        var a = d.snapshot().apuRender().getFirst();
        var block = a.bloques().getFirst();
        var rows = new ArrayList<ApuRow>();
        for (int i = 0; i < 125; i++) {
            var v = block.filas().getFirst().detalle();
            var detail = new Detalle(
                    v.id(),
                    (short) i,
                    false,
                    "FILA" + i,
                    "u",
                    v.cantidad(),
                    v.rendimiento(),
                    v.tarifaJornal(),
                    v.precioUnitarioTarifa(),
                    v.precioEfectivo(),
                    v.costoHora(),
                    new BigDecimal("98765.43"),
                    v.insumo());
            rows.add(row(detail, "FILA" + i + " " + "descripción ñ ".repeat(i == 60 ? 650 : 3) + " FIN" + i));
        }
        var longBlock =
                new ApuBlock(block.tipo(), block.etiqueta(), block.ordenCapturado(), rows, block.subtotal(), true);
        a = copy(a, a.codigo(), a.etiquetaCodigo(), a.descripcion(), List.of(longBlock), a.pie());
        d = ApuFixtures.apus(d, List.of(a, d.snapshot().apuRender().get(1)));
        try (var pdf = Loader.loadPDF(render(d))) {
            assertTrue(pdf.getNumberOfPages() > 4);
            String all = new PDFTextStripper().getText(pdf);
            assertEquals(125, occurrences(all, "98765.43"));
            assertEquals(650 + 124 * 3, occurrences(all, "descripción"));
            assertTrue(all.indexOf("FIN124") < all.indexOf("Costo directo"));
            for (int i = 0; i < 125; i++) assertTrue(all.contains("FIN" + i), "FIN" + i);
            int last = pdf.getNumberOfPages();
            for (int p = 1; p < last; p++) {
                String text = page(pdf, p);
                assertTrue(text.contains(a.id().toString()));
                assertFalse(text.contains("COD1"));
                if (text.contains("descripción") || text.contains("FILA")) {
                    for (String header :
                            List.of("Descripción", "Cantidad", "Tarifa", "Costo hora", "Rendimiento", "Costo"))
                        assertTrue(text.contains(header), "page " + p + " missing " + header);
                    assertTrue(text.indexOf("Descripción") < text.indexOf("descripción"));
                }
                assertTrue(text.contains("Página " + p + " de " + last));
            }
            assertTrue(page(pdf, last).contains("Código: 2 COD1"));
            assertTrue(page(pdf, last).contains("Costo total"));
            geometryAndSafety(pdf);
        }
    }

    @Test
    void giantHeaderCodeInstitutionSignerAndFooterAreRebuilt() throws Exception {
        var base = documento(true, 2, 4);
        String giant = "Inicioñ\n" + "Ω texto á\t".repeat(900) + "Finñ";
        var p = base.snapshot().proyecto();
        var a = base.snapshot().apuRender().getFirst();
        var pie = new ApuFooter(
                a.pie().subtotales(),
                a.pie().porcentajeIndirecto(),
                a.pie().costoDirecto(),
                a.pie().costoIndirecto(),
                a.pie().costoTotal(),
                a.pie().valorOfertado(),
                "PIE " + giant);
        var signer = base.snapshot().firmantes().getFirst();
        var proyecto = new Proyecto(
                p.id(),
                p.nombre(),
                p.codigo(),
                p.descripcion(),
                p.anio(),
                p.fechaInicio(),
                p.plazoEjecucion(),
                p.plazoUnidad(),
                p.estado(),
                "INSTITUCION " + giant,
                null);
        var d = ApuFixtures.with(
                base,
                proyecto,
                null,
                List.of(new Firmante(signer.id(), "FIRMA " + giant, signer.cargo(), signer.rol(), signer.orden())),
                List.of(copy(a, "CODIGO " + giant, "DETALLE " + giant, a.bloques(), pie)));
        try (var pdf = Loader.loadPDF(render(d))) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(pdf.getNumberOfPages() > 3);
            assertEquals(5 * 900, occurrences(text, "texto"));
            assertEquals(5, occurrences(text, "Finñ"));
            assertTrue(text.contains("FIRMA"));
            assertTrue(text.contains("PIE"));
            geometryAndSafety(pdf);
        }
    }

    @Test
    void materialAndTransportRepeatTheirOwnHeadersOnContinuation() throws Exception {
        var d = documento(true, 2, 4);
        var a = d.snapshot().apuRender().getFirst();
        var blocks = new ArrayList<ApuBlock>();
        for (var b : a.bloques().subList(2, 4)) {
            var source = a.bloques().get(2).filas().getFirst();
            blocks.add(new ApuBlock(
                    b.tipo(),
                    b.etiqueta(),
                    b.ordenCapturado(),
                    List.of(row(source.detalle(), b.tipo() + " " + "material ñ ".repeat(2200) + " FINSECCION")),
                    b.subtotal(),
                    true));
        }
        d = ApuFixtures.apus(d, List.of(copy(a, a.codigo(), a.etiquetaCodigo(), a.descripcion(), blocks, a.pie())));
        try (var pdf = Loader.loadPDF(render(d))) {
            assertTrue(pdf.getNumberOfPages() > 2);
            assertEquals(2, occurrences(new PDFTextStripper().getText(pdf), "12.00"));
            for (int p = 1; p <= pdf.getNumberOfPages(); p++) {
                String text = page(pdf, p);
                if (text.contains("material ñ")) {
                    for (String h : List.of("Descripción", "Unidad", "Cantidad", "Costo"))
                        assertTrue(text.contains(h), text);
                    assertTrue(text.contains("Precio unitario") || text.contains("Tarifa"), text);
                    assertFalse(text.contains("Rendimiento"));
                }
            }
            geometryAndSafety(pdf);
        }
    }

    @Test
    void hmInRealPositionIndividualCiAndTextWithoutSpaces() throws Exception {
        var d = ApuFixtures.display(load("apus-ci0123456-pdf"), 2, 4);
        var a = d.snapshot().apuRender().get(1);
        var b = a.bloques().getFirst();
        var hm = b.filas().getLast();
        var v = b.filas().getFirst().detalle();
        var huge = new Detalle(
                v.id(),
                v.orden(),
                false,
                "W".repeat(2000),
                "w".repeat(2000),
                v.cantidad(),
                v.rendimiento(),
                v.tarifaJornal(),
                v.precioUnitarioTarifa(),
                v.precioEfectivo(),
                v.costoHora(),
                new BigDecimal("87654.32"),
                v.insumo());
        var equipo = new ApuBlock(
                b.tipo(), b.etiqueta(), b.ordenCapturado(), List.of(hm, row(v, "DESPUESHM")), b.subtotal(), false);
        var material = a.bloques().get(2);
        var longMaterial = new ApuBlock(
                material.tipo(),
                material.etiqueta(),
                material.ordenCapturado(),
                List.of(row(huge, huge.descripcion())),
                material.subtotal(),
                true);
        d = ApuFixtures.apus(
                d, List.of(copy(a, "C".repeat(2000), "IDENTIFICADOR", List.of(equipo, longMaterial), a.pie())));
        try (var pdf = Loader.loadPDF(render(d))) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.indexOf("Herramienta Menor 7.3000%MO") < text.indexOf("DESPUESHM"));
            assertTrue(text.contains("Costo indirecto 12.3456% 4.99"));
            assertEquals(1, occurrences(text, "87654.32"));
            assertEquals(2000, text.chars().filter(c -> c == 'W').count());
            assertEquals(2000, text.chars().filter(c -> c == 'w').count());
            assertTrue(text.replaceAll("\\s+", "").contains("C".repeat(2000)));
            geometryAndSafety(pdf);
        }
    }

    @Test
    void rejectsClosedOptionsWithoutFallback() {
        var d = documento(true, 2, 4);
        for (var o : List.of(
                new Object[] {"xlsx", Map.of("layout", "pestanas")},
                new Object[] {"pdf", Map.of("orientacion", "horizontal")},
                new Object[] {"pdf", Map.of("orientacion", "vertical")},
                new Object[] {"pdf", Map.of("layout", "apilado")},
                new Object[] {"mspdi", Map.of()})) {
            @SuppressWarnings("unchecked")
            var projection = as(d, (String) o[0], (Map<String, String>) o[1]);
            assertThrows(IllegalArgumentException.class, () -> ApuPdfRenderer.renderizar(projection));
        }
    }

    // ---- CHK-26/27 ----

    @Test
    void everyApuCarriesProjectIdentityAndSignersInOrder() throws Exception {
        var projection = load("apus-identidad-pdf");
        var proyecto = projection.snapshot().proyecto();
        try (var pdf = Loader.loadPDF(ApuPdfRenderer.renderizar(projection))) {
            String text = new PDFTextStripper().getText(pdf);
            assertEquals(2, occurrences(text, "PROYECTO: " + proyecto.nombre()));
            assertEquals(2, occurrences(text, "Año: " + proyecto.anio()));
            assertEquals(2, occurrences(text, "CONSOLIDADO POR:"));
            assertEquals(2, occurrences(text, "APROBADO POR:"));
            assertEquals(6, occurrences(text, "FIRMA:"));
            assertTrue(text.indexOf("CONSOLIDADO POR:") < text.indexOf("Carla"));
            assertTrue(text.indexOf("Carla") < text.indexOf("APROBADO POR:"));
            assertTrue(text.indexOf("Beto Uno") < text.indexOf("Ana Dos"));
        }
    }

    // ---- L4 (ApuDocumentoEvidenceIT): "APU " + id on every page in order; labeled footer at display HALF_UP ----

    private static void identityAndFooterParity(DocumentProjectionV1 projection) throws Exception {
        var apus = projection.snapshot().apuRender();
        int precision = projection.snapshot().display().precision();
        try (var pdf = Loader.loadPDF(render(projection))) {
            var textById = new LinkedHashMap<UUID, StringBuilder>();
            for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
                String text = page(pdf, i);
                var id = apus.stream()
                        .map(ApuView::id)
                        .filter(uuid -> text.contains("APU " + uuid))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("page without APU identity"));
                textById.computeIfAbsent(id, ignored -> new StringBuilder()).append(text);
                assertTrue(pdf.getPage(i - 1).getMediaBox().getWidth() > 0);
            }
            assertEquals(apus.stream().map(ApuView::id).toList(), new ArrayList<>(textById.keySet()));
            for (var a : apus) {
                String text = textById.get(a.id()).toString();
                var values = List.of(
                        a.pie().costoDirecto(),
                        a.pie().costoIndirecto(),
                        a.pie().costoTotal(),
                        a.pie().valorOfertado());
                var labels = List.of("Costo directo", "Costo indirecto", "Costo total", "Valor ofertado");
                for (int i = 0; i < labels.size(); i++) {
                    String money = values.get(i)
                            .setScale(precision, RoundingMode.HALF_UP)
                            .toPlainString();
                    String label = labels.get(i);
                    assertTrue(
                            text.lines().anyMatch(line -> line.startsWith(label) && line.endsWith(money)),
                            a.id() + " labeled footer " + label + " " + money);
                }
            }
        }
    }

    @Test
    void identityOnEveryPageAndLabeledFooterForCapturedFixtures() throws Exception {
        for (String fixture : List.of("apus-hostil-pdf", "apus-identidad-pdf")) identityAndFooterParity(load(fixture));
        identityAndFooterParity(ApuFixtures.display(load("apus-conciliado-pestanas"), 2, 4));
    }

    @Test
    void stressThreeApusOf125NegativeRowsAtPrecisionZeroAndTwo() throws Exception {
        for (int precision : new int[] {0, 2}) identityAndFooterParity(ApuFixtures.extremes(precision));
    }
}
