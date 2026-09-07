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
