package ec.uce.propuestas.documento;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfWriter;
import ec.uce.propuestas.documento.exportacion.ApuDocumentoProyeccion.*;
import ec.uce.propuestas.documento.exportacion.OpcionesDocumento;
import ec.uce.propuestas.documento.exportacion.SnapshotDocumento;
import ec.uce.propuestas.motor.SeccionTipo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure A4 portrait serialization; persisted amounts never come from the diagnostic calculation. */
public final class ApuPdfWriter {
    private static volatile BaseFont fuente;

    private ApuPdfWriter() {}

    public static byte[] renderizar(Documento documento, OpcionesDocumento opciones) {
        if (!"pdf".equals(opciones.formato()) || !opciones.opciones().isEmpty()) {
            throw new IllegalArgumentException("Opciones incompatibles con APU PDF");
        }
        if (documento.apus().isEmpty()) throw new IllegalArgumentException("Documento APU vacío");
        var formato = new Formato(documento, cargarFuente());
        try (var body = new ByteArrayOutputStream();
                var output = new ByteArrayOutputStream()) {
            // Body excludes the bounded identifier and pagination bands on every page.
            var doc = new Document(new Rectangle(595, 842), 24, 24, 62, 40);
            var writer = PdfWriter.getInstance(doc, body);
            var header = new Identificador(formato.normal);
            writer.setPageEvent(header);
            header.apu = documento.apus().getFirst();
            doc.open();
            boolean first = true;
            for (var a : documento.apus()) {
                header.apu = a;
                if (!first) doc.newPage();
                first = false;
                bloque(doc, documento, a, formato);
            }
            doc.close();
            var reader = new PdfReader(body.toByteArray());
            try {
                var stamper = new PdfStamper(reader, output);
                int pages = reader.getNumberOfPages();
                for (int p = 1; p <= pages; p++) {
                    ColumnText.showTextAligned(
                            stamper.getOverContent(p),
                            Element.ALIGN_CENTER,
                            new Phrase("Página " + p + " de " + pages, formato.normal),
                            297.5f,
                            20,
                            0);
                }
                stamper.close();
            } finally {
                reader.close();
            }
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF de APUs", e);
        }
    }

    private static void bloque(Document doc, Documento d, Analisis a, Formato f) throws Exception {
        linea(doc, "ANÁLISIS DE PRECIOS UNITARIOS", f.bold);
        linea(doc, d.proyecto().direccionInstitucional(), f.normal);
        linea(doc, d.proyecto().subdireccionInstitucional(), f.normal);
        if (a.nombreProyectoHeader() != null) linea(doc, "Proyecto: " + a.nombreProyectoHeader(), f.normal);
        linea(doc, "Código: " + a.etiquetaCodigo(), f.bold);
        linea(doc, a.descripcion(), f.normal);
        linea(doc, "Unidad: " + a.unidad(), f.normal);
        for (var b : a.bloques()) {
            boolean horario = b.tipo() == SeccionTipo.EQUIPO || b.tipo() == SeccionTipo.MANO_OBRA;
            var table = tabla(
                    horario ? new float[] {3.4f, 1, 1.3f, 1.2f, 1.3f, 1.2f} : new float[] {4, 1, 1.3f, 1.7f, 1.4f});
            var title = celda(b.etiqueta(), f.bold, false);
            title.setColspan(table.getNumberOfColumns());
            table.addCell(title);
            for (String label : horario
                    ? List.of("Descripción", "Cantidad", "Tarifa / Jornal", "Costo hora", "Rendimiento", "Costo")
                    : List.of("Descripción", "Unidad", "Cantidad", "Precio / Tarifa", "Costo")) {
                table.addCell(celda(label, f.bold, false));
            }
            // Both section name and its own column schema repeat, never a previous section's schema.
            table.setHeaderRows(2);
            for (var row : b.filas()) {
                var v = row.detalle();
                String descripcion = row.porcentajeHerramientaMenor() == null
                        ? row.descripcion()
                        : "Herramienta Menor " + f.porcentaje(row.porcentajeHerramientaMenor()) + "MO";
                var textos = fragmentos(descripcion);
                var unidades = horario ? List.of("") : fragmentos(v.unidad());
                for (int i = 0; i < Math.max(textos.size(), unidades.size()); i++) {
                    table.addCell(celda(i < textos.size() ? textos.get(i) : "", f.normal, false));
                    if (!horario) table.addCell(celda(i < unidades.size() ? unidades.get(i) : "", f.normal, false));
                    table.addCell(celda(i == 0 ? efectivo(v.cantidad()) : "", f.normal, true));
                    table.addCell(celda(i == 0 ? f.dinero(v.precioEfectivo()) : "", f.normal, true));
                    if (horario) {
                        table.addCell(celda(i == 0 ? f.dinero(v.costoHora()) : "", f.normal, true));
                        table.addCell(celda(i == 0 ? efectivo(v.rendimiento()) : "", f.normal, true));
                    }
                    table.addCell(celda(i == 0 ? f.dinero(v.costo()) : "", f.normal, true));
                }
            }
            // A header-only table is suppressed by OpenPDF; an explicit empty row keeps empty sections visible.
            if (b.filas().isEmpty()) {
                for (int i = 0; i < table.getNumberOfColumns(); i++) table.addCell(celda("", f.normal, false));
            }
            if (b.mostrarSubtotal()) subtotal(table, "Subtotal " + b.etiqueta(), f.dinero(b.subtotal()), f);
            doc.add(table);
        }
        for (var subtotal : a.pie().subtotales()) {
            // Reuse projected labels, including captured suffix policy, even for hidden empty blocks.
            String label = etiqueta(subtotal.tipo(), d.parametros().sufijosSeccionActivos());
            importe(doc, "Subtotal " + label, f.dinero(subtotal.valor()), f);
        }
        importe(doc, "Costo directo", f.dinero(a.pie().costoDirecto()), f);
        importe(
                doc,
                "Costo indirecto " + f.porcentaje(a.pie().porcentajeIndirecto()),
                f.dinero(a.pie().costoIndirecto()),
                f);
        importe(doc, "Costo total", f.dinero(a.pie().costoTotal()), f);
        importe(doc, "Valor ofertado", f.dinero(a.pie().valorOfertado()), f);
        linea(doc, a.pie().mensaje(), f.normal);
        for (var firmante : d.firmantes().stream()
                .sorted(Comparator.comparingInt(SnapshotDocumento.Firmante::orden)
                        .thenComparing(s -> s.id().toString()))
                .toList()) {
            linea(
                    doc,
                    PresupuestoXlsxWriter.seguro(firmante.nombre()) + " · "
                            + PresupuestoXlsxWriter.seguro(firmante.cargo()) + " · "
                            + PresupuestoXlsxWriter.seguro(firmante.rol()),
                    f.normal);
        }
    }

    private static String etiqueta(SeccionTipo tipo, boolean sufijos) {
        return switch (tipo) {
            case EQUIPO -> "Equipos y herramientas" + (sufijos ? " (M)" : "");
            case MANO_OBRA -> "Mano de obra" + (sufijos ? " (N)" : "");
            case MATERIAL -> "Materiales" + (sufijos ? " (O)" : "");
            case TRANSPORTE -> "Transporte" + (sufijos ? " (P)" : "");
        };
    }

    private static void subtotal(PdfPTable table, String label, String amount, Formato f) {
        var cell = celda(label, f.bold, false);
        cell.setColspan(table.getNumberOfColumns() - 1);
        table.addCell(cell);
        table.addCell(celda(amount, f.bold, true));
    }

    private static void importe(Document doc, String label, String amount, Formato f) throws Exception {
        var table = tabla(new float[] {4, 1});
        subtotal(table, label, amount, f);
        doc.add(table);
    }

    private static void linea(Document doc, String value, Font font) throws Exception {
        if (value == null || value.isBlank()) return;
        var table = tabla(new float[] {1});
        for (String chunk : fragmentos(value)) table.addCell(celda(chunk, font, false));
        doc.add(table);
    }

    private static PdfPTable tabla(float[] widths) throws Exception {
        var table = new PdfPTable(widths.length);
        table.setWidths(widths);
        table.setWidthPercentage(100);
        // Every physical text chunk is bounded even in the narrow unit column. A row cannot
        // exceed the 740pt body; atomic rows keep first-row amounts from being repeated.
        table.setSplitRows(false);
        table.setSplitLate(false);
        return table;
    }

    private static PdfPCell celda(String value, Font font, boolean numero) {
        var cell = new PdfPCell(new Phrase(value, font));
        cell.setPadding(3);
        cell.setLeading(10, 0);
        cell.setHorizontalAlignment(numero ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        return cell;
    }

    private static List<String> fragmentos(String value) {
        var result = new ArrayList<String>();
        for (String chunk : ApuTextoPresentacion.fragmentos(value)) {
            // The shared helper bounds spreadsheet rows. A narrower PDF column needs a
            // second, Unicode-safe physical bound (including newlines and unbroken words).
            int start = 0;
            while (start < chunk.length()) {
                int end = chunk.offsetByCodePoints(start, Math.min(80, chunk.codePointCount(start, chunk.length())));
                if (end < chunk.length()) {
                    int boundary = end;
                    while (boundary > start && !Character.isWhitespace(chunk.codePointBefore(boundary))) {
                        boundary = chunk.offsetByCodePoints(boundary, -1);
                    }
                    if (boundary > start) end = boundary;
                }
                result.add(chunk.substring(start, end));
                start = end;
            }
        }
        return result.isEmpty() ? List.of("") : result;
    }

    private static String efectivo(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static final class Formato {
        final Font normal, bold;
        final int dinero, porcentaje;

        Formato(Documento d, BaseFont bf) {
            normal = new Font(bf, 8);
            bold = new Font(bf, 8, Font.BOLD);
            dinero = d.display().precision();
            porcentaje = d.display().precisionPorcentaje();
        }

        String dinero(BigDecimal value) {
            return value == null
                    ? ""
                    : value.setScale(dinero, RoundingMode.HALF_UP).toPlainString();
        }

        String porcentaje(BigDecimal value) {
            return value == null
                    ? ""
                    : value.movePointRight(2)
                                    .setScale(porcentaje, RoundingMode.HALF_UP)
                                    .toPlainString() + "%";
        }
    }

    private static final class Identificador extends PdfPageEventHelper {
        final Font font;
        Analisis apu;

        Identificador(Font font) {
            this.font = font;
        }

        @Override
        public void onStartPage(PdfWriter writer, Document doc) {
            ColumnText.showTextAligned(
                    writer.getDirectContent(), Element.ALIGN_LEFT, new Phrase("APU " + apu.id(), font), 24, 818, 0);
            String code = PresupuestoXlsxWriter.seguro(apu.codigo());
            // Full hostile/long codes remain in the flowing body, never in a fixed-height header.
            String label = code.codePointCount(0, code.length()) <= 40 && !code.contains("\n")
                    ? "Código: " + code
                    : "Código extenso (ver encabezado)";
            ColumnText.showTextAligned(
                    writer.getDirectContent(), Element.ALIGN_LEFT, new Phrase(label, font), 24, 804, 0);
        }
    }

    private static BaseFont cargarFuente() {
        BaseFont cached = fuente;
        if (cached != null) return cached;
        synchronized (ApuPdfWriter.class) {
            if (fuente != null) return fuente;
            try (var in = ApuPdfWriter.class.getResourceAsStream("/fonts/LiberationSans-Regular.ttf")) {
                if (in == null) throw new IllegalStateException("No se encontró la fuente embebida");
                fuente = BaseFont.createFont(
                        "LiberationSans-Regular.ttf",
                        BaseFont.IDENTITY_H,
                        BaseFont.EMBEDDED,
                        true,
                        in.readAllBytes(),
                        null);
                return fuente;
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo cargar la fuente embebida", e);
            }
        }
    }
}
