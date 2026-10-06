package ec.uce.propuestas.documento;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.documento.exportacion.ApuDocumentoProyeccion;
import ec.uce.propuestas.documento.exportacion.ApuDocumentoProyeccion.*;
import ec.uce.propuestas.documento.exportacion.OpcionesDocumento;
import ec.uce.propuestas.documento.exportacion.SnapshotDocumento;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

class ApuPdfWriterTest {
    private static final OpcionesDocumento PDF = new OpcionesDocumento("pdf", Map.of());

    @Test
    void cortoA4NuevaPaginaPorIdentidadFuenteEmbebidaEInerte() throws Exception {
        var d = documento(true, 2, 4);
        try (var pdf = Loader.loadPDF(render(d))) {
            assertEquals(2, pdf.getNumberOfPages());
            for (int i = 0; i < 2; i++) {
                String text = pagina(pdf, i + 1);
                assertTrue(text.contains(d.apus().get(i).id().toString()));
                assertTrue(text.contains("Código: " + (i + 1) + " COD" + i));
                assertTrue(text.contains("ANÁLISIS DE PRECIOS UNITARIOS"));
                assertTrue(text.contains("ñ m² m³ á é í ó ú ü ¿ ¡ Ω"));
                assertTrue(text.contains("=<script> @SUM(A1) https://example.org"));
                assertTrue(text.contains("Página " + (i + 1) + " de 2"));
                assertFalse(text.contains(d.apus().get(1 - i).id().toString()));
            }
            comprobarGeometriaYSeguridad(pdf);
        }
    }

    @Test
    void importesPersistidosHmOrdenYPrecisionIndependiente() throws Exception {
        try (var pdf = Loader.loadPDF(render(documento(true, 2, 4)))) {
            String text = pagina(pdf, 1);
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
                    "7.3000%MO")) {
                assertTrue(text.contains(value), value + " missing: " + text);
            }
            assertTrue(text.indexOf("Descripción") < text.indexOf("Herramienta Menor"));
            assertTrue(text.indexOf("@SUM(A1)") < text.indexOf("Herramienta Menor"));
            assertTrue(text.indexOf("Equipos y herramientas (M)") < text.indexOf("Mano de obra (N)"));
            assertTrue(text.indexOf("Mano de obra (N)") < text.indexOf("Materiales (O)"));
            assertTrue(text.indexOf("Materiales (O)") < text.indexOf("Transporte (P)"));
            assertEquals(2, ocurrencias(text, "Subtotal Equipos y herramientas (M)"));
            assertTrue(text.contains("Costo total 123.12"));
            assertTrue(text.contains("Valor ofertado 123.12"));
            assertTrue(text.indexOf("Legal ñ") < text.indexOf("Técnico ñ"));
            assertFalse(text.contains("999999"));
            assertFalse(text.contains("Costo directo ajustado"));
        }
    }

    @Test
    void togglesAusentesNoFabricanInstitucionNiFirmantes() throws Exception {
        var original = documento(false, 2, 1);
        var proyecto = original.proyecto();
        var d = new Documento(
                original.presupuestoId(),
                original.version(),
                new SnapshotDocumento.Proyecto(
                        proyecto.id(),
                        proyecto.nombre(),
                        proyecto.codigo(),
                        proyecto.descripcion(),
                        proyecto.anio(),
                        proyecto.fechaInicio(),
                        proyecto.plazoEjecucion(),
                        proyecto.plazoUnidad(),
                        proyecto.estado(),
                        null,
                        null),
                original.parametros(),
                original.display(),
                List.of(),
                original.apus());
        try (var pdf = Loader.loadPDF(render(d))) {
            String text = new PDFTextStripper().getText(pdf);
            for (String absent : List.of(
                    "Proyecto:",
                    "Subtotal",
                    "Mano de obra",
                    "Transporte",
                    "Legal ñ",
                    "Técnico ñ",
                    "Dirección institucional",
                    "(M)",
                    "Código: 1")) {
                assertFalse(text.contains(absent), absent);
            }
            assertTrue(text.contains("Equipos y herramientas"));
            assertTrue(text.contains("Materiales"));
            assertTrue(text.contains("18.0%"));
        }
    }

    @Test
    void halfUpCeroYDosNegativosSinRedondearCantidadRendimiento() throws Exception {
        for (int precision : new int[] {0, 2}) {
            var d = documento(true, precision, 3);
            var a = d.apus().getFirst();
            var pie = new Pie(
                    a.pie().subtotales(),
                    new BigDecimal("0.123456"),
                    new BigDecimal("2.345"),
                    new BigDecimal("-2.355"),
                    new BigDecimal("-1.5"),
                    new BigDecimal("2.5"),
                    "FINPIE");
            d = reemplazar(d, List.of(copiar(a, a.codigo(), a.descripcion(), a.bloques(), pie)));
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
    void apuLargoContinuaSeccionSinDuplicarImportesNiCrearPaginasBlancas() throws Exception {
        var d = documento(true, 2, 4);
        var a = d.apus().getFirst();
        var bloque = a.bloques().getFirst();
        var filas = new ArrayList<Fila>();
        for (int i = 0; i < 125; i++) {
            var v = bloque.filas().getFirst().detalle();
            var detalle = new SnapshotDocumento.Detalle(
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
            filas.add(new Fila(
                    detalle, "FILA" + i + " " + "descripción ñ ".repeat(i == 60 ? 650 : 3) + " FIN" + i, null));
        }
        var largo =
                new Bloque(bloque.tipo(), bloque.etiqueta(), bloque.ordenCapturado(), filas, bloque.subtotal(), true);
        a = copiar(a, a.codigo(), a.descripcion(), List.of(largo), a.pie());
        d = reemplazar(d, List.of(a, d.apus().get(1)));
        try (var pdf = Loader.loadPDF(render(d))) {
            assertTrue(pdf.getNumberOfPages() > 4);
            String all = new PDFTextStripper().getText(pdf);
            assertEquals(125, ocurrencias(all, "98765.43"));
            assertEquals(650 + 124 * 3, ocurrencias(all, "descripción"));
            assertTrue(all.indexOf("FIN124") < all.indexOf("Costo directo"));
            for (int i = 0; i < 125; i++) assertTrue(all.contains("FIN" + i), "FIN" + i);
            int ultima = pdf.getNumberOfPages();
            for (int p = 1; p < ultima; p++) {
                String text = pagina(pdf, p);
                assertTrue(text.contains(a.id().toString()));
                assertFalse(text.contains("COD1"));
                if (text.contains("descripción") || text.contains("FILA")) {
                    for (String header : List.of(
                            "Descripción", "Cantidad", "Tarifa / Jornal", "Costo hora", "Rendimiento", "Costo")) {
                        assertTrue(text.contains(header), "Page " + p + " missing " + header);
                    }
                    assertTrue(text.indexOf("Descripción") < text.indexOf("descripción"));
                }
                assertTrue(text.contains("Página " + p + " de " + ultima));
            }
            assertTrue(pagina(pdf, ultima).contains("Código: 2 COD1"));
            assertTrue(pagina(pdf, ultima).contains("Costo total"));
            comprobarGeometriaYSeguridad(pdf);
        }
    }

    @Test
    void encabezadoCodigoInstitucionFirmanteYPieGigantesSeReconstruyen() throws Exception {
        var base = documento(true, 2, 4);
        String gigante = "Inicioñ\n" + "Ω texto á\t".repeat(900) + "Finñ";
        var p = base.proyecto();
        var a = base.apus().getFirst();
        var pie = new Pie(
                a.pie().subtotales(),
                a.pie().porcentajeIndirecto(),
                a.pie().costoDirecto(),
                a.pie().costoIndirecto(),
                a.pie().costoTotal(),
                a.pie().valorOfertado(),
                "PIE " + gigante);
        var firma = base.firmantes().getFirst();
        var d = new Documento(
                base.presupuestoId(),
                base.version(),
                new SnapshotDocumento.Proyecto(
                        p.id(),
                        p.nombre(),
                        p.codigo(),
                        p.descripcion(),
                        p.anio(),
                        p.fechaInicio(),
                        p.plazoEjecucion(),
                        p.plazoUnidad(),
                        p.estado(),
                        "INSTITUCION " + gigante,
                        null),
                base.parametros(),
                base.display(),
                List.of(new SnapshotDocumento.Firmante(
                        firma.id(), "FIRMA " + gigante, firma.cargo(), firma.rol(), firma.orden())),
                List.of(copiar(a, "CODIGO " + gigante, "DETALLE " + gigante, a.bloques(), pie)));
        try (var pdf = Loader.loadPDF(render(d))) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(pdf.getNumberOfPages() > 3);
            assertEquals(5 * 900, ocurrencias(text, "texto"));
            assertEquals(5, ocurrencias(text, "Finñ"));
            assertTrue(text.contains("FIRMA"));
            assertTrue(text.contains("PIE"));
            comprobarGeometriaYSeguridad(pdf);
        }
    }

    @Test
    void materialTransporteRepitenCabecerasPropiasEnContinuacion() throws Exception {
        var d = documento(true, 2, 4);
        var a = d.apus().getFirst();
        var bloques = new ArrayList<Bloque>();
        for (var b : a.bloques().subList(2, 4)) {
            var fila = a.bloques().get(2).filas().getFirst();
            bloques.add(new Bloque(
                    b.tipo(),
                    b.etiqueta(),
                    b.ordenCapturado(),
                    List.of(new Fila(
                            fila.detalle(), b.tipo() + " " + "material ñ ".repeat(2200) + " FINSECCION", null)),
                    b.subtotal(),
                    true));
        }
        d = reemplazar(d, List.of(copiar(a, a.codigo(), a.descripcion(), bloques, a.pie())));
        try (var pdf = Loader.loadPDF(render(d))) {
            assertTrue(pdf.getNumberOfPages() > 2);
            assertEquals(2, ocurrencias(new PDFTextStripper().getText(pdf), "12.00"));
            for (int p = 1; p <= pdf.getNumberOfPages(); p++) {
                String text = pagina(pdf, p);
                if (text.contains("material ñ")) {
                    for (String h : List.of("Descripción", "Unidad", "Cantidad", "Precio / Tarifa", "Costo"))
                        assertTrue(text.contains(h), text);
                    assertFalse(text.contains("Rendimiento"));
                }
            }
            comprobarGeometriaYSeguridad(pdf);
        }
    }

    @Test
    void hmEnPosicionRealCiIndividualYTextoSinEspacios() throws Exception {
        var d = ApuDocumentoProyeccion.proyectar(ApuDocumentoFixture.snapshot(true, true, new BigDecimal("0.123456")));
        d = new Documento(
                d.presupuestoId(),
                d.version(),
                d.proyecto(),
                d.parametros(),
                new SnapshotDocumento.Display(2, 4),
                d.firmantes(),
                d.apus());
        var a = d.apus().get(1);
        var b = a.bloques().getFirst();
        var hm = b.filas().getLast();
        var v = b.filas().getFirst().detalle();
        var enorme = new SnapshotDocumento.Detalle(
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
        var filas = List.of(hm, new Fila(v, "DESPUESHM", null));
        var equipo = new Bloque(b.tipo(), b.etiqueta(), b.ordenCapturado(), filas, b.subtotal(), false);
        var material = a.bloques().get(2);
        var largo = new Bloque(
                material.tipo(),
                material.etiqueta(),
                material.ordenCapturado(),
                List.of(new Fila(enorme, enorme.descripcion(), null)),
                material.subtotal(),
                true);
        d = reemplazar(d, List.of(copiar(a, "C".repeat(2000), "IDENTIFICADOR", List.of(equipo, largo), a.pie())));
        try (var pdf = Loader.loadPDF(render(d))) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.indexOf("Herramienta Menor 7.3000%MO") < text.indexOf("DESPUESHM"));
            assertTrue(text.contains("Costo indirecto 12.3456% 4.99"));
            assertEquals(1, ocurrencias(text, "87654.32"));
            assertEquals(2000, text.chars().filter(c -> c == 'W').count());
            assertEquals(2000, text.chars().filter(c -> c == 'w').count());
            assertTrue(text.replaceAll("\\s+", "").contains("C".repeat(2000)));
            comprobarGeometriaYSeguridad(pdf);
        }
    }

    @Test
    void rechazaOpcionesCerradasSinFallback() {
        var d = documento(true, 2, 4);
        for (var o : List.of(
                new OpcionesDocumento("xlsx", Map.of("layout", "pestanas")),
                new OpcionesDocumento("pdf", Map.of("orientacion", "horizontal")),
                new OpcionesDocumento("pdf", Map.of("orientacion", "vertical")),
                new OpcionesDocumento("pdf", Map.of("layout", "apilado")),
                new OpcionesDocumento("mspdi", Map.of()))) {
            assertThrows(IllegalArgumentException.class, () -> ApuPdfWriter.renderizar(d, o));
        }
    }

    private static Documento documento(boolean activos, int money, int pct) {
        var d = ApuDocumentoProyeccion.proyectar(ApuDocumentoFixture.snapshot(activos, false, null));
        var apus = new ArrayList<Analisis>();
        for (int i = 0; i < d.apus().size(); i++) {
            var a = d.apus().get(i);
            apus.add(new Analisis(
                    a.id(),
                    "COD" + i,
                    (activos ? (i + 1) + " " : "") + "COD" + i,
                    "ñ m² m³ á é í ó ú ü ¿ ¡ Ω =<script> @SUM(A1) https://example.org",
                    a.unidad(),
                    a.especificacionTecnica(),
                    a.nombreProyectoHeader(),
                    a.bloques(),
                    a.pie(),
                    a.diagnostico()));
        }
        return new Documento(
                d.presupuestoId(),
                d.version(),
                d.proyecto(),
                d.parametros(),
                new SnapshotDocumento.Display(money, pct),
                d.firmantes(),
                apus);
    }

    private static Analisis copiar(Analisis a, String codigo, String descripcion, List<Bloque> bloques, Pie pie) {
        return new Analisis(
                a.id(),
                codigo,
                codigo,
                descripcion,
                a.unidad(),
                a.especificacionTecnica(),
                a.nombreProyectoHeader(),
                bloques,
                pie,
                a.diagnostico());
    }

    private static Documento reemplazar(Documento d, List<Analisis> apus) {
        return new Documento(
                d.presupuestoId(), d.version(), d.proyecto(), d.parametros(), d.display(), d.firmantes(), apus);
    }

    private static byte[] render(Documento d) {
        return ApuPdfWriter.renderizar(d, PDF);
    }

    private static int ocurrencias(String text, String value) {
        return text.split(java.util.regex.Pattern.quote(value), -1).length - 1;
    }

    private static String pagina(PDDocument pdf, int page) throws Exception {
        var stripper = new PDFTextStripper();
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        return stripper.getText(pdf);
    }

    private static void comprobarGeometriaYSeguridad(PDDocument pdf) throws Exception {
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
                        "Text must remain in identifier, body, or pagination band: " + position.getUnicode() + " y="
                                + y);
                assertTrue(
                        position.getYDirAdj() >= 15 && position.getYDirAdj() <= 825,
                        position.getUnicode() + " y=" + position.getYDirAdj());
                assertTrue(
                        position.getXDirAdj() >= 23 && position.getXDirAdj() + position.getWidthDirAdj() <= 573,
                        position.getUnicode() + " x=" + position.getXDirAdj());
                super.processTextPosition(position);
            }
        }.getText(pdf);
    }
}
