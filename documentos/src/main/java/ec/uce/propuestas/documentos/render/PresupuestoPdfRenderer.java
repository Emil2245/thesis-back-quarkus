package ec.uce.propuestas.documentos.render;

import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.Capitulo;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.Firmante;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.Snapshot;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfStamper;
import org.openpdf.text.pdf.PdfWriter;

/** Renders projected budget values without accessing or recalculating domain data. */
public final class PresupuestoPdfRenderer {
    private static final String[] HEADERS = {
        "Ítem", "Código", "Descripción", "Unidad", "Cantidad", "P.Unitario", "P.Total"
    };
    private static volatile BaseFont fuente;

    private PresupuestoPdfRenderer() {}

    public static byte[] renderizar(DocumentProjectionV1 projection) {
        Snapshot snapshot = Objects.requireNonNull(projection).snapshot();
        boolean horizontal = "horizontal".equals(projection.opciones().get("orientacion"));
        BaseFont base = fuente();
        Font normal = new Font(base, 8);
        Font bold = new Font(base, 8, Font.BOLD);
        try (var body = new ByteArrayOutputStream();
                var output = new ByteArrayOutputStream()) {
            Document doc = new Document(horizontal ? new Rectangle(842, 595) : new Rectangle(595, 842), 24, 24, 24, 40);
            PdfWriter.getInstance(doc, body);
            doc.open();
            for (String line : identificacion(snapshot)) doc.add(new Paragraph(line, bold));
            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[] {0.7f, 1.1f, 3.2f, 0.7f, 1.1f, 1.1f, 1.1f});
            table.setSpacingBefore(12);
            table.setHeaderRows(1);
            table.setSplitRows(false);
            table.setSplitLate(false);
            for (String header : HEADERS) celda(table, header, bold, false);
            int precision = snapshot.display().precision();
            for (Fila row : filasPresentacion(snapshot)) {
                Font font = row.capitulo() ? bold : normal;
                for (String text : row.textos()) celda(table, text, font, false);
                for (BigDecimal value : row.numeros()) celda(table, visible(value, precision), font, true);
            }
            celda(table, "TOTAL", bold, false);
            for (int i = 1; i < 6; i++) celda(table, "", bold, false);
            celda(table, visible(snapshot.total(), precision), bold, true);
            doc.add(table);
            for (String line : cierre(snapshot)) doc.add(new Paragraph(line, normal));
            doc.close();

            PdfReader reader = new PdfReader(body.toByteArray());
            try {
                PdfStamper stamper = new PdfStamper(reader, output);
                int pages = reader.getNumberOfPages();
                for (int page = 1; page <= pages; page++) {
                    Rectangle size = reader.getPageSize(page);
                    ColumnText.showTextAligned(
                            stamper.getOverContent(page),
                            Element.ALIGN_CENTER,
                            new Phrase("Página " + page + " de " + pages, normal),
                            size.getWidth() / 2,
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

    private static void celda(PdfPTable table, String text, Font font, boolean numeric) {
        var cell = new PdfPCell(new Phrase(seguro(text), font));
        cell.setPadding(4);
        cell.setHorizontalAlignment(numeric ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        table.addCell(cell);
    }

    private static BaseFont fuente() {
        BaseFont cached = fuente;
        if (cached != null) return cached;
        synchronized (PresupuestoPdfRenderer.class) {
            if (fuente != null) return fuente;
            try (var in = PresupuestoPdfRenderer.class.getResourceAsStream("/fonts/LiberationSans-Regular.ttf")) {
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

    private static String seguro(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Cc}&&[^\\n\\t]]", "");
    }

    private static String visible(BigDecimal value, int precision) {
        return value == null
                ? ""
                : value.setScale(precision, RoundingMode.HALF_UP).toPlainString();
    }

    private static List<String> identificacion(Snapshot s) {
        var result = new ArrayList<String>();
        result.add("PRESUPUESTO");
        if (s.proyecto().direccionInstitucional() != null
                && !s.proyecto().direccionInstitucional().isBlank())
            result.add(seguro(s.proyecto().direccionInstitucional()));
        if (s.proyecto().subdireccionInstitucional() != null
                && !s.proyecto().subdireccionInstitucional().isBlank())
            result.add(seguro(s.proyecto().subdireccionInstitucional()));
        result.add("PROYECTO: " + seguro(s.proyecto().nombre()) + " · Código: "
                + seguro(s.proyecto().codigo()));
        result.add("TABLA DE DESCRIPCIÓN DE RUBROS, UNIDADES, CANTIDADES Y PRECIOS");
        result.add("Año: " + s.proyecto().anio() + " · Versión: " + s.version());
        result.add("Presupuesto: " + s.presupuestoId());
        return result;
    }

    private static List<String> cierre(Snapshot s) {
        var result = new ArrayList<String>();
        result.add("Nota: los precios no incluyen IVA");
        if (s.notas() != null && !s.notas().isBlank()) result.add(seguro(s.notas()));
        var roles = new LinkedHashMap<String, List<Firmante>>();
        for (String role : List.of("CONSOLIDADO", "APROBADO")) roles.put(role, new ArrayList<>());
        s.firmantes().stream()
                .sorted(Comparator.comparingInt(Firmante::orden).thenComparing(f -> f.id().toString()))
                .forEach(f ->
                        roles.computeIfAbsent(f.rol(), r -> new ArrayList<>()).add(f));
        roles.forEach((role, signers) -> {
            if (signers.isEmpty()) return;
            boolean known = role.equals("CONSOLIDADO") || role.equals("APROBADO");
            result.add(seguro(role) + (known ? " POR:" : ":"));
            for (var signer : signers) {
                result.add(seguro(signer.nombre()) + "\n" + seguro(signer.cargo()));
                result.add("FIRMA:");
            }
        });
        if (s.parametros().mensajeFooter() != null
                && !s.parametros().mensajeFooter().isBlank())
            result.add(seguro(s.parametros().mensajeFooter()));
        return result;
    }

    private record Fila(boolean capitulo, List<String> textos, List<BigDecimal> numeros) {}

    private static List<Fila> filasPresentacion(Snapshot snapshot) {
        var rows = new ArrayList<Fila>();
        for (var chapter : snapshot.capitulos()) recorrer(chapter, rows);
        var result = new ArrayList<Fila>();
        for (Fila row : rows) {
            List<String> chunks = fragmentos(row.textos().get(2));
            for (int i = 0; i < chunks.size(); i++) {
                result.add(new Fila(
                        row.capitulo(),
                        i == 0
                                ? List.of(
                                        row.textos().get(0),
                                        row.textos().get(1),
                                        chunks.get(i),
                                        row.textos().get(3))
                                : List.of("(continuación)", "", chunks.get(i), ""),
                        i == 0 ? row.numeros() : Arrays.asList(null, null, null)));
            }
        }
        return result;
    }

    private static void recorrer(Capitulo chapter, List<Fila> rows) {
        rows.add(new Fila(
                true,
                List.of(seguro(chapter.item()), "", seguro(chapter.descripcion()), ""),
                Arrays.asList(null, null, chapter.total())));
        for (var child : chapter.hijos()) recorrer(child, rows);
        chapter.rubros().stream()
                .sorted((a, b) -> {
                    int order = compararItem(a.item(), b.item());
                    return order != 0 ? order : a.id().toString().compareTo(b.id().toString());
                })
                .forEach(r -> rows.add(new Fila(
                        false,
                        List.of(seguro(r.item()), seguro(r.codigo()), seguro(r.descripcion()), seguro(r.unidad())),
                        List.of(r.cantidad(), r.precioUnitario(), r.precioTotal()))));
    }

    private static int compararItem(String a, String b) {
        String[] aa = seguro(a).split("\\.", -1), bb = seguro(b).split("\\.", -1);
        for (int i = 0; i < Math.min(aa.length, bb.length); i++) {
            int cmp = aa[i].matches("[0-9]+") && bb[i].matches("[0-9]+")
                    ? new BigInteger(aa[i]).compareTo(new BigInteger(bb[i]))
                    : aa[i].compareTo(bb[i]);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(aa.length, bb.length);
    }

    // Match XLSX/Core physical rows so an oversized description survives page breaks
    // while its financial values appear exactly once.
    private static List<String> fragmentos(String text) {
        int lines = 0;
        for (String line : text.split("\n", -1)) lines += Math.max(1, (line.length() + 29) / 30);
        if (text.length() <= 32767 && Math.min(409, Math.max(24, lines * 15)) <= 240) return List.of(text);
        var result = new ArrayList<String>();
        int start = 0;
        while (start < text.length()) {
            int end = start, count = 1, columns = 0, boundary = start;
            while (end < text.length()) {
                int cp = text.codePointAt(end);
                int nextCount = count, nextColumns = columns + (cp == '\t' ? 4 : 1);
                if (cp == '\n') {
                    nextCount++;
                    nextColumns = 0;
                } else if (nextColumns > 30) {
                    nextCount++;
                    nextColumns = 1;
                }
                if (nextCount > 10) break;
                end += Character.charCount(cp);
                count = nextCount;
                columns = nextColumns;
                if (Character.isWhitespace(cp)) boundary = end;
            }
            if (end < text.length() && boundary > start) end = boundary;
            result.add(text.substring(start, end));
            start = end;
        }
        return result.isEmpty() ? List.of("") : result;
    }
}
