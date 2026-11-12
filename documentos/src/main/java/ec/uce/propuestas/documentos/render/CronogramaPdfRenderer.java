package ec.uce.propuestas.documentos.render;

import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1;
import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1.Sheet;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.openpdf.text.*;
import org.openpdf.text.pdf.*;

/** Renders the captured sheet without deriving any domain values. */
public final class CronogramaPdfRenderer {
    private static final String[] HEADERS = {
        "Item", "Código", "Descripción", "Unidad", "Cant.", "P.Unit.", "P.Total", "Peso"
    };
    private static volatile BaseFont font;

    private CronogramaPdfRenderer() {}

    public static byte[] renderizar(CronogramaProjectionV1 projection) {
        Objects.requireNonNull(projection, "projection");
        if (!"pdf".equals(projection.format())
                || projection.sheet() == null
                || !("a4".equals(projection.paper()) || "a3".equals(projection.paper()))) {
            throw new IllegalArgumentException("Expected PDF projection with A4 or A3 paper");
        }
        Sheet p = projection.sheet();
        try (var out = new ByteArrayOutputStream()) {
            Rectangle page = "a3".equals(projection.paper()) ? new Rectangle(1191, 842) : new Rectangle(842, 595);
            Document doc = new Document(page, 18, 18, 28, 24);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            BaseFont base = font();
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter w, Document d) {
                    ColumnText.showTextAligned(
                            w.getDirectContent(),
                            Element.ALIGN_CENTER,
                            new Phrase("Sistema APU · Cronograma Valorado · " + LocalDate.now(), new Font(base, 8)),
                            (d.left() + d.right()) / 2,
                            d.bottom() - 12,
                            0);
                }
            });
            doc.open();
            Font normal = new Font(base, 7), bold = new Font(base, 7, Font.BOLD), header = new Font(base, 9, Font.BOLD);
            centered(doc, "CRONOGRAMA VALORADO DE TRABAJOS", new Font(base, 14, Font.BOLD));
            centered(
                    doc,
                    "Proyecto: " + neutral(p.projectName()) + "  ·  Código: " + neutral(p.projectCode())
                            + "  ·  Año: " + p.year() + "  ·  Unidad: " + p.timeUnit() + "  ·  Períodos: "
                            + p.periodCount()
                            + (p.startDate() == null ? "" : "  ·  Inicio: " + p.startDate()),
                    normal);
            String institution = java.util.stream.Stream.of(p.institution(), p.subdirection())
                    .filter(s -> s != null && !s.isBlank())
                    .collect(java.util.stream.Collectors.joining(" · "));
            if (!institution.isEmpty()) centered(doc, "Institución: " + neutral(institution), normal);
            doc.add(new Paragraph(" ", header));
            float available = page.getWidth() - 36;
            float[] fixed = {32, 45, 140, 32, 55, 65, 65, 50};
            String periodLabel = "SEMANA".equalsIgnoreCase(p.timeUnit()) ? "Semana" : "Mes";
            float periodWidth = Math.max(52, base.getWidthPoint(periodLabel + " " + p.periodCount(), 9) + 6);
            var totals = List.of(
                    p.totals().partialPercentage(),
                    p.totals().accumulatedPercentage(),
                    p.totals().partialAmount(),
                    p.totals().accumulatedAmount());
            for (int group = 0; group < totals.size(); group++) {
                int precision = group < 2 ? p.percentagePrecision() : p.moneyPrecision();
                for (String value : totals.get(group))
                    periodWidth = Math.max(periodWidth, base.getWidthPoint(display(value, precision), 7) + 6);
            }
            for (var row : p.rows()) {
                String[] values = {
                    display(row.quantity(), p.moneyPrecision()),
                    display(row.unitPrice(), p.moneyPrecision()),
                    display(row.totalPrice(), p.moneyPrecision()),
                    display(row.weight(), p.percentagePrecision())
                };
                for (int i = 0; i < values.length; i++)
                    fixed[i + 4] = Math.max(fixed[i + 4], base.getWidthPoint(values[i], 7) + 6);
                for (String value : row.percentByPeriod())
                    if (value != null)
                        periodWidth = Math.max(
                                periodWidth, base.getWidthPoint(display(value, p.percentagePrecision()), 7) + 6);
            }
            float fixedWidth = 0;
            for (float width : fixed) fixedWidth += width;
            int perBlock = Math.max(1, (int) ((available - fixedWidth) / periodWidth));
            for (int from = 0; from < p.periodCount(); from += perBlock) {
                int to = Math.min(p.periodCount(), from + perBlock), columns = 8 + to - from;
                if (from > 0) doc.newPage();
                float[] widths = Arrays.copyOf(fixed, columns);
                Arrays.fill(widths, 8, columns, (available - fixedWidth) / (to - from));
                PdfPTable body = new PdfPTable(columns);
                body.setWidthPercentage(100);
                body.setSplitLate(true);
                body.setWidths(widths);
                String name = neutral(p.projectName());
                PdfPCell identity = new PdfPCell(new Phrase(
                        "Proyecto: " + (name.length() > 80 ? name.substring(0, 80) + "…" : name) + " · Períodos "
                                + (from + 1) + "–" + to + " de " + p.periodCount(),
                        normal));
                identity.setColspan(columns);
                identity.setPadding(3);
                body.addCell(identity);
                Color headBg = new Color(220, 220, 220),
                        chapterBg = new Color(235, 235, 235),
                        summaryBg = new Color(255, 245, 200);
                for (String h : HEADERS) cell(body, h, header, headBg, Element.ALIGN_CENTER);
                for (int i = from; i < to; i++)
                    cell(body, periodLabel + " " + (i + 1), header, headBg, Element.ALIGN_CENTER);
                body.setHeaderRows(2);
                PdfPTable table = body;
                int chapter = 0;
                for (int index = 0; index < p.rows().size(); index++) {
                    while (chapter < p.chapters().size()
                            && p.chapters().get(chapter).beforeRow() <= index) {
                        var c = p.chapters().get(chapter++);
                        cell(table, "Cap. " + neutral(c.item()), bold, chapterBg, Element.ALIGN_LEFT);
                        cell(table, "", bold, chapterBg, Element.ALIGN_LEFT);
                        cell(table, neutral(c.description()), bold, chapterBg, Element.ALIGN_LEFT);
                        for (int i = 3; i < 6; i++) cell(table, "", bold, chapterBg, Element.ALIGN_LEFT);
                        cell(table, display(c.total(), p.moneyPrecision()), bold, chapterBg, Element.ALIGN_RIGHT);
                        for (int i = 7; i < columns; i++) cell(table, "", bold, chapterBg, Element.ALIGN_LEFT);
                    }
                    if (index == p.rows().size() - 1) {
                        table = new PdfPTable(columns);
                        table.setWidths(widths);
                    }
                    var r = p.rows().get(index);
                    cell(table, neutral(r.item()), normal, null, Element.ALIGN_LEFT);
                    cell(table, neutral(r.code()), normal, null, Element.ALIGN_LEFT);
                    cell(table, neutral(r.description()), normal, null, Element.ALIGN_LEFT);
                    cell(table, neutral(r.unit()), normal, null, Element.ALIGN_CENTER);
                    for (String value : List.of(
                            display(r.quantity(), p.moneyPrecision()),
                            display(r.unitPrice(), p.moneyPrecision()),
                            display(r.totalPrice(), p.moneyPrecision()),
                            display(r.weight(), p.percentagePrecision())))
                        cell(table, value, normal, null, Element.ALIGN_RIGHT);
                    for (int i = from; i < to; i++)
                        cell(
                                table,
                                display(r.percentByPeriod().get(i), p.percentagePrecision()),
                                normal,
                                null,
                                Element.ALIGN_RIGHT);
                }
                summary(
                        table,
                        "% PARCIAL",
                        p.totals().partialPercentage(),
                        from,
                        to,
                        p.percentagePrecision(),
                        bold,
                        summaryBg);
                summary(
                        table,
                        "% ACUMULADO",
                        p.totals().accumulatedPercentage(),
                        from,
                        to,
                        p.percentagePrecision(),
                        bold,
                        summaryBg);
                summary(
                        table,
                        "MONTO PARCIAL",
                        p.totals().partialAmount(),
                        from,
                        to,
                        p.moneyPrecision(),
                        bold,
                        summaryBg);
                summary(
                        table,
                        "MONTO ACUMULADO",
                        p.totals().accumulatedAmount(),
                        from,
                        to,
                        p.moneyPrecision(),
                        bold,
                        summaryBg);
                if (table != body) {
                    PdfPCell end = new PdfPCell(table);
                    end.setColspan(columns);
                    end.setPadding(0);
                    end.setBorder(Rectangle.NO_BORDER);
                    body.addCell(end);
                }
                doc.add(body);
            }
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo generar el PDF del cronograma", e);
        }
    }

    private static void centered(Document doc, String text, Font font) throws DocumentException {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_CENTER);
        doc.add(p);
    }

    private static void summary(
            PdfPTable table, String label, List<String> values, int from, int to, int precision, Font font, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(label, font));
        c.setColspan(8);
        c.setBackgroundColor(bg);
        c.setPadding(2);
        table.addCell(c);
        for (int i = from; i < to; i++) cell(table, display(values.get(i), precision), font, bg, Element.ALIGN_RIGHT);
    }

    private static void cell(PdfPTable table, String text, Font font, Color bg, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(2);
        if (bg != null) c.setBackgroundColor(bg);
        table.addCell(c);
    }

    private static String display(String value, int precision) {
        return value == null
                ? ""
                : new BigDecimal(value)
                        .setScale(precision, RoundingMode.HALF_UP)
                        .toPlainString();
    }

    private static String neutral(String value) {
        if (value == null) return "";
        int i = 0;
        while (i < value.length() && value.charAt(i) <= ' ') i++;
        return i < value.length() && "=+-@".indexOf(value.charAt(i)) >= 0 ? "'" + value : value;
    }

    private static BaseFont font() throws Exception {
        BaseFont result = font;
        if (result == null)
            synchronized (CronogramaPdfRenderer.class) {
                result = font;
                if (result == null) {
                    try (var in =
                            CronogramaPdfRenderer.class.getResourceAsStream("/fonts/LiberationSans-Regular.ttf")) {
                        if (in == null) throw new IllegalStateException("Missing embedded Liberation Sans font");
                        result = BaseFont.createFont(
                                "LiberationSans-Regular.ttf",
                                BaseFont.IDENTITY_H,
                                BaseFont.EMBEDDED,
                                true,
                                in.readAllBytes(),
                                null);
                        font = result;
                    }
                }
            }
        return result;
    }
}
