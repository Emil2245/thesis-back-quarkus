package ec.uce.propuestas.documentos.render;

import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfStamper;
import org.openpdf.text.pdf.PdfWriter;

/** A4 serialization of Core's ordered and reconciled APU presentation, without recalculation. */
public final class ApuPdfRenderer {
    private static volatile BaseFont font;

    private ApuPdfRenderer() {}

    public static byte[] renderizar(DocumentProjectionV1 projection) {
        if (!"apus".equals(projection.documento())
                || !"pdf".equals(projection.formato())
                || !projection.opciones().isEmpty()) {
            throw new IllegalArgumentException("Opciones incompatibles con APU PDF");
        }
        var snapshot = projection.snapshot();
        if (snapshot.apuRender().isEmpty()) throw new IllegalArgumentException("Documento APU vacío");
        var format = new Format(snapshot.display(), loadFont());
        try (var body = new ByteArrayOutputStream();
                var output = new ByteArrayOutputStream()) {
            var doc = new Document(new Rectangle(595, 842), 24, 24, 62, 40);
            var writer = PdfWriter.getInstance(doc, body);
            var header = new Identifier(format.normal);
            header.apu = snapshot.apuRender().getFirst();
            writer.setPageEvent(header);
            doc.open();
            boolean first = true;
            for (var apu : snapshot.apuRender()) {
                header.apu = apu;
                if (!first) doc.newPage();
                first = false;
                block(doc, snapshot, apu, format);
            }
            doc.close();
            var reader = new PdfReader(body.toByteArray());
            try {
                var stamper = new PdfStamper(reader, output);
                int pages = reader.getNumberOfPages();
                for (int page = 1; page <= pages; page++) {
                    ColumnText.showTextAligned(
                            stamper.getOverContent(page),
                            Element.ALIGN_CENTER,
                            new Phrase("Página " + page + " de " + pages, format.normal),
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

    private static void block(Document doc, Snapshot snapshot, ApuView apu, Format f) throws Exception {
        line(doc, "ANÁLISIS DE PRECIOS UNITARIOS", f.bold);
        line(doc, snapshot.proyecto().direccionInstitucional(), f.normal);
        line(doc, snapshot.proyecto().subdireccionInstitucional(), f.normal);
        line(doc, "PROYECTO: " + snapshot.proyecto().nombre(), f.bold);
        line(doc, "Año: " + snapshot.proyecto().anio(), f.normal);
        if (apu.nombreProyectoHeader() != null) line(doc, "Proyecto: " + apu.nombreProyectoHeader(), f.normal);
        line(doc, "Código: " + apu.etiquetaCodigo(), f.bold);
        line(doc, apu.descripcion(), f.normal);
        line(doc, "Unidad: " + apu.unidad(), f.normal);
        for (var section : apu.bloques()) {
            boolean hourly = section.tipo() == SeccionTipo.EQUIPO || section.tipo() == SeccionTipo.MANO_OBRA;
            var table = table(
                    hourly ? new float[] {3.4f, 1, 1.3f, 1.2f, 1.3f, 1.2f} : new float[] {4, 1, 1.3f, 1.7f, 1.4f});
            var title = cell(section.etiqueta(), f.bold, false);
            title.setColspan(table.getNumberOfColumns());
            table.addCell(title);
            for (String label : headers(section.tipo())) table.addCell(cell(label, f.bold, false));
            table.setHeaderRows(2);
            for (var row : section.filas()) {
                var detail = row.detalle();
                String description = row.porcentajeHerramientaMenor() == null
                        ? row.descripcion()
                        : "Herramienta Menor " + f.percent(row.porcentajeHerramientaMenor()) + "MO";
                var descriptions = chunks(description);
                var units = hourly ? List.of("") : chunks(detail.unidad());
                for (int i = 0; i < Math.max(descriptions.size(), units.size()); i++) {
                    table.addCell(cell(i < descriptions.size() ? descriptions.get(i) : "", f.normal, false));
                    if (!hourly) table.addCell(cell(i < units.size() ? units.get(i) : "", f.normal, false));
                    table.addCell(cell(i == 0 ? effective(detail.cantidad()) : "", f.normal, true));
                    table.addCell(cell(i == 0 ? f.money(detail.precioEfectivo()) : "", f.normal, true));
                    if (hourly) {
                        table.addCell(cell(i == 0 ? f.money(row.costoHora()) : "", f.normal, true));
                        table.addCell(cell(i == 0 ? effective(detail.rendimiento()) : "", f.normal, true));
                    }
                    table.addCell(cell(i == 0 ? f.money(row.costo()) : "", f.normal, true));
                }
            }
            if (section.filas().isEmpty()) {
                for (int i = 0; i < table.getNumberOfColumns(); i++) table.addCell(cell("", f.normal, false));
            }
            if (section.mostrarSubtotal())
                subtotal(table, "Subtotal " + section.etiqueta(), f.money(section.subtotal()), f);
            doc.add(table);
        }
        var footer = apu.pie();
        for (var subtotal : footer.subtotales()) {
            amount(
                    doc,
                    "Subtotal "
                            + sectionTitle(
                                    subtotal.tipo(), snapshot.parametros().sufijosSeccionActivos()),
                    f.money(subtotal.valor()),
                    f);
        }
        amount(doc, "Costo directo", f.money(footer.costoDirecto()), f);
        amount(doc, "Costo indirecto " + f.percent(footer.porcentajeIndirecto()), f.money(footer.costoIndirecto()), f);
        amount(doc, "Costo total", f.money(footer.costoTotal()), f);
        amount(doc, "Valor ofertado", f.money(footer.valorOfertado()), f);
        line(doc, footer.mensaje(), f.normal);
        signatures(doc, snapshot.firmantes(), f);
    }

    private static List<String> headers(SeccionTipo type) {
        return switch (type) {
            case EQUIPO -> List.of("Descripción", "Cantidad", "Tarifa", "Costo hora", "Rendimiento", "Costo");
            case MANO_OBRA -> List.of("Descripción", "Cantidad", "Jornal/hr", "Costo hora", "Rendimiento", "Costo");
            case MATERIAL -> List.of("Descripción", "Unidad", "Cantidad", "Precio unitario", "Costo");
            case TRANSPORTE -> List.of("Descripción", "Unidad", "Cantidad", "Tarifa", "Costo");
        };
    }

    private static String sectionTitle(SeccionTipo type, boolean suffix) {
        String name = switch (type) {
            case EQUIPO -> "EQUIPOS";
            case MANO_OBRA -> "MANO DE OBRA";
            case MATERIAL -> "MATERIALES";
            case TRANSPORTE -> "TRANSPORTE";
        };
        String letter = switch (type) {
            case EQUIPO -> "M";
            case MANO_OBRA -> "N";
            case MATERIAL -> "O";
            case TRANSPORTE -> "P";
        };
        return suffix ? name + " (" + letter + ")" : name;
    }

    private static void signatures(Document doc, List<Firmante> signers, Format f) throws Exception {
        Map<String, List<Firmante>> groups = new LinkedHashMap<>();
        groups.put("CONSOLIDADO", new ArrayList<>());
        groups.put("APROBADO", new ArrayList<>());
        signers.stream()
                .sorted(Comparator.comparingInt(Firmante::orden).thenComparing(s -> s.id().toString()))
                .forEach(s -> groups.computeIfAbsent(s.rol(), ignored -> new ArrayList<>())
                        .add(s));
        for (var group : groups.entrySet()) {
            if (group.getValue().isEmpty()) continue;
            String role = group.getKey();
            line(doc, safe(role) + (role.equals("CONSOLIDADO") || role.equals("APROBADO") ? " POR:" : ":"), f.bold);
            for (var signer : group.getValue()) {
                line(doc, safe(signer.nombre()) + "\n" + safe(signer.cargo()), f.normal);
                line(doc, "FIRMA:", f.normal);
            }
        }
    }

    private static void subtotal(PdfPTable table, String label, String value, Format f) {
        var cell = cell(label, f.bold, false);
        cell.setColspan(table.getNumberOfColumns() - 1);
        table.addCell(cell);
        table.addCell(cell(value, f.bold, true));
    }

    private static void amount(Document doc, String label, String value, Format f) throws Exception {
        var table = table(new float[] {4, 1});
        subtotal(table, label, value, f);
        doc.add(table);
    }

    private static void line(Document doc, String value, Font font) throws Exception {
        if (value == null || value.isBlank()) return;
        var table = table(new float[] {1});
        for (String chunk : chunks(value)) table.addCell(cell(chunk, font, false));
        doc.add(table);
    }

    private static PdfPTable table(float[] widths) throws Exception {
        var table = new PdfPTable(widths.length);
        table.setWidths(widths);
        table.setWidthPercentage(100);
        table.setSplitRows(false);
        table.setSplitLate(false);
        return table;
    }

    private static PdfPCell cell(String text, Font font, boolean numeric) {
        var cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(3);
        cell.setLeading(10, 0);
        cell.setHorizontalAlignment(numeric ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        return cell;
    }

    private static String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Cc}&&[^\\n\\t]]", "");
    }

    // Limit both width and explicit line breaks so a physical table row always fits a page.
    private static List<String> chunks(String value) {
        String text = safe(value);
        var result = new ArrayList<String>();
        int start = 0;
        while (start < text.length()) {
            int end = start, count = 0, lines = 0, boundary = start;
            while (end < text.length() && count < 80 && lines < 10) {
                int cp = text.codePointAt(end);
                end += Character.charCount(cp);
                count++;
                if (cp == '\n') lines++;
                if (Character.isWhitespace(cp)) boundary = end;
            }
            if (end < text.length() && boundary > start) end = boundary;
            result.add(text.substring(start, end));
            start = end;
        }
        return result.isEmpty() ? List.of("") : result;
    }

    private static String effective(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static final class Format {
        final Font normal, bold;
        final int money, percent;

        Format(Display display, BaseFont base) {
            normal = new Font(base, 8);
            bold = new Font(base, 8, Font.BOLD);
            money = display.precision();
            percent = display.precisionPorcentaje();
        }

        String money(BigDecimal value) {
            return value == null
                    ? ""
                    : value.setScale(money, RoundingMode.HALF_UP).toPlainString();
        }

        String percent(BigDecimal value) {
            return value == null
                    ? ""
                    : value.movePointRight(2)
                                    .setScale(percent, RoundingMode.HALF_UP)
                                    .toPlainString() + "%";
        }
    }

    private static final class Identifier extends PdfPageEventHelper {
        final Font font;
        ApuView apu;

        Identifier(Font font) {
            this.font = font;
        }

        @Override
        public void onStartPage(PdfWriter writer, Document doc) {
            ColumnText.showTextAligned(
                    writer.getDirectContent(), Element.ALIGN_LEFT, new Phrase("APU " + apu.id(), font), 24, 818, 0);
            String code = safe(apu.codigo());
            String label = code.codePointCount(0, code.length()) <= 40 && !code.contains("\n")
                    ? "Código: " + code
                    : "Código extenso (ver encabezado)";
            ColumnText.showTextAligned(
                    writer.getDirectContent(), Element.ALIGN_LEFT, new Phrase(label, font), 24, 804, 0);
        }
    }

    private static BaseFont loadFont() {
        BaseFont cached = font;
        if (cached != null) return cached;
        synchronized (ApuPdfRenderer.class) {
            if (font != null) return font;
            try (var in = ApuPdfRenderer.class.getResourceAsStream("/fonts/LiberationSans-Regular.ttf")) {
                if (in == null) throw new IllegalStateException("No se encontró la fuente embebida");
                font = BaseFont.createFont(
                        "LiberationSans-Regular.ttf",
                        BaseFont.IDENTITY_H,
                        BaseFont.EMBEDDED,
                        true,
                        in.readAllBytes(),
                        null);
                return font;
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo cargar la fuente embebida", e);
            }
        }
    }
}
