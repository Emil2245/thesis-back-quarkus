package ec.uce.propuestas.documento;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfWriter;
import ec.uce.propuestas.documento.exportacion.OpcionesDocumento;
import ec.uce.propuestas.documento.exportacion.SnapshotDocumento;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** A4 paginado, exclusivamente sobre valores persistidos del snapshot seleccionado. */
public final class PresupuestoPdfWriter {
    private PresupuestoPdfWriter() {}

    private static volatile BaseFont fuente;

    public static byte[] renderizar(SnapshotDocumento snapshot, OpcionesDocumento opciones) {
        boolean horizontal = "horizontal".equals(opciones.opciones().get("orientacion"));
        BaseFont bf = cargarFuente();
        Font normal = new Font(bf, 8);
        Font bold = new Font(bf, 8, Font.BOLD);
        try (var body = new ByteArrayOutputStream();
                var output = new ByteArrayOutputStream()) {
            // Explicit rectangles: OpenPDF 2.0.3 rotate does not swap the dimensions.
            Document doc = new Document(horizontal ? new Rectangle(842, 595) : new Rectangle(595, 842), 24, 24, 24, 40);
            PdfWriter.getInstance(doc, body);
            doc.open();
            for (String linea : PresupuestoXlsxWriter.identificacion(snapshot)) doc.add(new Paragraph(linea, bold));
            PdfPTable tabla = new PdfPTable(7);
            tabla.setWidthPercentage(100);
            tabla.setWidths(new float[] {0.7f, 1.1f, 3.2f, 0.7f, 1.1f, 1.1f, 1.1f});
            tabla.setSpacingBefore(12);
            tabla.setHeaderRows(1);
            // Physical description chunks fit a page; never split a financial row
            // internally, which can displace headers during giant-row continuation.
            tabla.setSplitRows(false);
            tabla.setSplitLate(false);
            for (String header : PresupuestoXlsxWriter.CABECERAS) celda(tabla, header, bold, false);
            int precision = snapshot.display().precision();
            for (var fila : PresupuestoXlsxWriter.filasPresentacion(snapshot)) {
                Font font = fila.capitulo() ? bold : normal;
                for (String texto : fila.textos()) celda(tabla, texto, font, false);
                for (var value : fila.numeros())
                    celda(tabla, PresupuestoXlsxWriter.visible(value, precision), font, true);
            }
            celda(tabla, "TOTAL", bold, false);
            for (int c = 1; c < 6; c++) celda(tabla, "", bold, false);
            celda(tabla, PresupuestoXlsxWriter.visible(snapshot.total(), precision), bold, true);
            doc.add(tabla);
            for (String linea : PresupuestoXlsxWriter.cierre(snapshot)) doc.add(new Paragraph(linea, normal));
            doc.close();

            // Pagination is presentation only. A second pass permits an exact n/m without
            // guessing the final page count or compressing content to a single page.
            PdfReader reader = new PdfReader(body.toByteArray());
            try {
                PdfStamper stamper = new PdfStamper(reader, output);
                int pages = reader.getNumberOfPages();
                for (int p = 1; p <= pages; p++) {
                    Rectangle page = reader.getPageSize(p);
                    ColumnText.showTextAligned(
                            stamper.getOverContent(p),
                            Element.ALIGN_CENTER,
                            new Phrase("Página " + p + " de " + pages, normal),
                            page.getWidth() / 2,
                            20,
                            0);
                }
                stamper.close();
            } finally {
                reader.close();
            }
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF del presupuesto", e);
        }
    }

    private static void celda(PdfPTable tabla, String texto, Font font, boolean numero) {
        var cell = new PdfPCell(new Phrase(PresupuestoXlsxWriter.seguro(texto), font));
        cell.setPadding(4);
        cell.setHorizontalAlignment(numero ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        tabla.addCell(cell);
    }

    private static BaseFont cargarFuente() {
        BaseFont cached = fuente;
        if (cached != null) return cached;
        synchronized (PresupuestoPdfWriter.class) {
            if (fuente != null) return fuente;
            try (var in = PresupuestoPdfWriter.class.getResourceAsStream("/fonts/LiberationSans-Regular.ttf")) {
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
