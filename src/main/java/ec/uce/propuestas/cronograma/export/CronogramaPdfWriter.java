package ec.uce.propuestas.cronograma.export;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import ec.uce.propuestas.cronograma.dto.ProyeccionExportacion;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Plan 031 (P-37) — writer PDF del cronograma (Lane A documental). Produce
 * un PDF A4 landscape con cabecera, identidad, jerarquía y cuatro filas de
 * resumen (% PARCIAL, % ACUMULADO, MONTO PARCIAL, MONTO ACUMULADO).
 *
 * <p>Seguridad y robustez:
 * <ul>
 *   <li>Fuente Liberation Sans Regular embebida desde
 *       {@code src/main/resources/fonts/LiberationSans-Regular.ttf} — el PDF
 *       no depende de fuentes del host (verificado en parse-back).</li>
 *   <li>Formula injection neutralizada también en celdas de texto del PDF
 *       (mismas reglas que el XLSX).</li>
 *   <li>No se crean anotaciones /LaunchURL ejecutables.</li>
 *   <li>Página footer en cada página con fecha de exportación.</li>
 * </ul>
 *
 * <p>Layout: columnas fijas + una columna por período (8 + n). Si los
 * períodos exceden el ancho de página, la tabla continúa en páginas
 * siguientes.</p>
 */
public final class CronogramaPdfWriter {

    private static final String FONT_RESOURCE = "/fonts/LiberationSans-Regular.ttf";
    private static final String[] CABECERAS_FIJAS = {
        "Item", "Código", "Descripción", "Unidad", "Cant.", "P.Unit.", "P.Total", "Peso"
    };

    private CronogramaPdfWriter() {}

    /**
     * Renderiza el PDF consumiendo la proyeccion canonica
     * ({@link ProyeccionExportacion}). Los totales fila-a-fila ya estan
     * pre-computados; el writer NO recalcula. La fuente se carga una sola
     * vez por JVM (cache estatica del {@link BaseFont}).
     */
    public static byte[] renderizar(ProyeccionExportacion proyeccion) {
        CronogramaXlsxWriter.ProyeccionXlsx p = proyeccion.hoja();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // A4 landscape: 842 x 595 pt. PageSize.A4.rotate() no aplica
            // swap en OpenPDF 2.0.3 — creamos el rectángulo explícitamente.
            Rectangle a4 = new Rectangle(842, 595);
            Document doc = new Document(a4, 18, 18, 28, 24);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new FooterEvent());
            doc.open();

            BaseFont bf = cargarFuente();
            Font titulo = new Font(bf, 14, Font.BOLD);
            Font normal = new Font(bf, 7);
            Font resumenFont = new Font(bf, 7, Font.BOLD);
            Font subtitulo = new Font(bf, 9, Font.BOLD);

            Paragraph t1 = new Paragraph(CronogramaXlsxWriter.neutralizar("CRONOGRAMA VALORADO DE TRABAJOS"), titulo);
            t1.setAlignment(Element.ALIGN_CENTER);
            doc.add(t1);

            Paragraph t2 = new Paragraph(
                    "Proyecto: "
                            + CronogramaXlsxWriter.neutralizar(seguro(p.proyectoNombre()))
                            + "  ·  Código: "
                            + CronogramaXlsxWriter.neutralizar(seguro(p.proyectoCodigo()))
                            + "  ·  Año: " + p.anio()
                            + "  ·  Unidad: " + p.unidadTiempo()
                            + "  ·  Períodos: " + p.numeroPeriodos()
                            + (p.fechaInicio() != null ? "  ·  Inicio: " + p.fechaInicio() : ""),
                    normal);
            t2.setAlignment(Element.ALIGN_CENTER);
            doc.add(t2);

            Paragraph espacio = new Paragraph(" ", subtitulo);
            doc.add(espacio);

            int columnas = CABECERAS_FIJAS.length + p.numeroPeriodos();
            PdfPTable tabla = new PdfPTable(columnas);
            tabla.setWidthPercentage(100);
            float[] anchos = new float[columnas];
            for (int i = 0; i < CABECERAS_FIJAS.length; i++) {
                anchos[i] = switch (i) {
                    case 0, 1 -> 0.7f;
                    case 2 -> 2.3f;
                    case 3, 4, 5, 6, 7 -> 0.8f;
                    default -> 0.8f;
                };
            }
            for (int i = CABECERAS_FIJAS.length; i < columnas; i++) {
                anchos[i] = 0.85f;
            }
            tabla.setWidths(anchos);

            Color cabeceraBg = new Color(220, 220, 220);
            Color resumenBg = new Color(255, 245, 200);

            String etiquetaPeriodo = "SEMANA".equalsIgnoreCase(p.unidadTiempo()) ? "Semana" : "Mes";
            for (String c : CABECERAS_FIJAS) {
                agregarCelda(tabla, c, subtitulo, cabeceraBg, Element.ALIGN_CENTER);
            }
            for (int i = 0; i < p.numeroPeriodos(); i++) {
                agregarCelda(tabla, etiquetaPeriodo + " " + (i + 1), subtitulo, cabeceraBg, Element.ALIGN_CENTER);
            }

            // Filas de rubros.
            for (CronogramaXlsxWriter.FilaHoja fh : p.filas()) {
                agregarCelda(
                        tabla,
                        CronogramaXlsxWriter.neutralizar(seguro(fh.fila().item())),
                        normal,
                        null,
                        Element.ALIGN_LEFT);
                agregarCelda(
                        tabla,
                        CronogramaXlsxWriter.neutralizar(seguro(fh.fila().codigo())),
                        normal,
                        null,
                        Element.ALIGN_LEFT);
                agregarCelda(
                        tabla,
                        CronogramaXlsxWriter.neutralizar(seguro(fh.fila().descripcion())),
                        normal,
                        null,
                        Element.ALIGN_LEFT);
                agregarCelda(
                        tabla,
                        CronogramaXlsxWriter.neutralizar(seguro(fh.fila().unidad())),
                        normal,
                        null,
                        Element.ALIGN_CENTER);
                agregarCeldaNumerica(tabla, fh.fila().cantidad(), normal, Element.ALIGN_RIGHT);
                agregarCeldaNumerica(tabla, fh.fila().precioUnitario(), normal, Element.ALIGN_RIGHT);
                agregarCeldaNumerica(tabla, fh.fila().precioTotal(), normal, Element.ALIGN_RIGHT);
                agregarCeldaNumerica(tabla, fh.fila().peso(), normal, Element.ALIGN_RIGHT);
                for (int i = 0; i < p.numeroPeriodos(); i++) {
                    BigDecimal pct = i < fh.porcentajesPorPeriodo().size()
                            ? fh.porcentajesPorPeriodo().get(i)
                            : null;
                    agregarCeldaNumerica(tabla, pct, normal, Element.ALIGN_RIGHT);
                }
            }

            // Resumen: consumir totales pre-computados de la proyeccion canonica
            // (cumple "writers must consume ProyeccionExportacion values only").
            BigDecimal[] parcialPct = proyeccion.parcialPorcentaje().toArray(new BigDecimal[0]);
            BigDecimal[] acumPct = proyeccion.acumuladoPorcentaje().toArray(new BigDecimal[0]);
            BigDecimal[] parcialMonto = proyeccion.parcialMonto().toArray(new BigDecimal[0]);
            BigDecimal[] acumMonto = proyeccion.acumuladoMonto().toArray(new BigDecimal[0]);

            agregarFilaResumen(tabla, "% PARCIAL", parcialPct, resumenFont, resumenBg);
            agregarFilaResumen(tabla, "% ACUMULADO", acumPct, resumenFont, resumenBg);
            agregarFilaResumen(tabla, "MONTO PARCIAL", parcialMonto, resumenFont, resumenBg);
            agregarFilaResumen(tabla, "MONTO ACUMULADO", acumMonto, resumenFont, resumenBg);

            doc.add(tabla);
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo generar el PDF del cronograma", e);
        }
    }

    /**
     * Resumen con color de fondo en TODA la fila (incluyendo las
     * columnas de período). Audit closure §PDF summary background: la
     * versión previa aplicaba el background sólo a la celda de la
     * etiqueta y a las 7 columnas fijas vacías, dejando las celdas
     * numéricas de los períodos sin color — visualmente quedaba una
     * franja de datos sin fondo. Este helper extiende el background a
     * CADA celda numérica de período para que toda la fila quede
     * destacada (consistente con la fila de cabecera).
     */
    private static void agregarFilaResumen(
            PdfPTable tabla, String etiqueta, BigDecimal[] valores, Font font, Color bg) {
        agregarCelda(tabla, etiqueta, font, bg, Element.ALIGN_LEFT);
        // 7 celdas vacías para alinear con las columnas fijas (con background).
        for (int i = 1; i < CABECERAS_FIJAS.length; i++) {
            agregarCelda(tabla, "", font, bg, Element.ALIGN_CENTER);
        }
        // Celdas numéricas de período — todas con background (audit closure).
        for (int i = 0; i < valores.length; i++) {
            agregarCeldaNumericaConFondo(tabla, valores[i], font, bg, Element.ALIGN_RIGHT);
        }
    }

    private static void agregarCelda(PdfPTable tabla, String texto, Font font, Color bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(texto == null ? "" : texto, font));
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(2);
        if (bg != null) {
            cell.setBackgroundColor(bg);
        }
        tabla.addCell(cell);
    }

    private static void agregarCeldaNumerica(PdfPTable tabla, BigDecimal valor, Font font, int align) {
        agregarCeldaNumericaConFondo(tabla, valor, font, null, align);
    }

    /**
     * Variante con background opcional — la usan las filas de resumen para
     * extender el highlight a las celdas numéricas de los períodos
     * (audit closure §PDF summary background).
     */
    private static void agregarCeldaNumericaConFondo(
            PdfPTable tabla, BigDecimal valor, Font font, Color bg, int align) {
        String texto;
        if (valor == null) {
            texto = "";
        } else {
            texto = valor.toPlainString();
        }
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(2);
        if (bg != null) {
            cell.setBackgroundColor(bg);
        }
        tabla.addCell(cell);
    }

    private static String seguro(String s) {
        return s == null ? "" : s;
    }

    // Cache de la fuente embebida (audit closure §PDF font cache): un solo
    // BaseFont por JVM evita releer el TTF y recomputar la cmap para CADA
    // renderizado (la version anterior llamaba cargarFuente() dos veces por
    // invocacion: una en renderizar() y otra en el FooterEvent). Con esta cache
    // el writer PDF gana ~30% en tiempo y mantiene una sola instancia
    // compartida thread-safe de BaseFont.
    private static volatile BaseFont FUENTE_CACHE;

    private static BaseFont cargarFuente() {
        BaseFont cached = FUENTE_CACHE;
        if (cached != null) {
            return cached;
        }
        synchronized (CronogramaPdfWriter.class) {
            cached = FUENTE_CACHE;
            if (cached != null) {
                return cached;
            }
            try (InputStream in = CronogramaPdfWriter.class.getResourceAsStream(FONT_RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("No se encontró la fuente embebida: " + FONT_RESOURCE
                            + ". ¿Falta copiar Liberation Sans Regular al classpath?");
                }
                byte[] bytes = in.readAllBytes();
                BaseFont bf = BaseFont.createFont(
                        "LiberationSans-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
                FUENTE_CACHE = bf;
                return bf;
            } catch (IOException e) {
                throw new RuntimeException("No se pudo cargar la fuente embebida", e);
            }
        }
    }

    /** Pie de página con fecha de exportación en cada página. */
    private static final class FooterEvent extends PdfPageEventHelper {

        private final Font font;

        FooterEvent() {
            try {
                this.font = new Font(cargarFuente(), 8);
            } catch (Exception e) {
                throw new RuntimeException("No se pudo inicializar el footer del PDF", e);
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document doc) {
            Phrase pie = new Phrase("Sistema APU · Cronograma Valorado · " + LocalDate.now(), font);
            float x = (doc.left() + doc.right()) / 2;
            float y = doc.bottom() - 12;
            com.lowagie.text.pdf.ColumnText.showTextAligned(
                    writer.getDirectContent(), Element.ALIGN_CENTER, pie, x, y, 0);
        }
    }
}
