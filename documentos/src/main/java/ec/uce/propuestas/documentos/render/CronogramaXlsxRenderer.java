package ec.uce.propuestas.documentos.render;

import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Renders the captured sheet values without recalculating the schedule. */
public final class CronogramaXlsxRenderer {
    private CronogramaXlsxRenderer() {}

    public static byte[] renderizar(CronogramaProjectionV1 projection) {
        if (projection == null || !"xlsx".equals(projection.format()) || projection.sheet() == null)
            throw new IllegalArgumentException("Expected XLSX cronograma projection");
        var p = projection.sheet();
        try (var wb = new XSSFWorkbook();
                var output = new ByteArrayOutputStream()) {
            var sh = wb.createSheet(sheetName(p.projectCode(), p.projectName()));
            Font bold = wb.createFont();
            bold.setBold(true);
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            CellStyle title = wb.createCellStyle();
            title.setFont(titleFont);
            title.setAlignment(HorizontalAlignment.CENTER);
            CellStyle header = wb.createCellStyle();
            header.setFont(bold);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            borders(header);
            CellStyle summary = wb.createCellStyle();
            summary.setFont(bold);
            summary.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            summary.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            borders(summary);
            CellStyle text = wb.createCellStyle();
            borders(text);
            text.setVerticalAlignment(VerticalAlignment.TOP);
            CellStyle chapter = wb.createCellStyle();
            chapter.setFont(bold);
            chapter.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            chapter.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            borders(chapter);
            CellStyle money = numeric(wb, text, p.moneyPrecision());
            CellStyle pct = numeric(wb, text, p.percentagePrecision());
            CellStyle summaryMoney = numeric(wb, summary, p.moneyPrecision());
            CellStyle summaryPct = numeric(wb, summary, p.percentagePrecision());
            CellStyle chapterMoney = numeric(wb, chapter, p.moneyPrecision());
            int columns = 8 + p.periodCount();
            cell(sh.createRow(0), 0, "CRONOGRAMA VALORADO DE TRABAJOS", title);
            sh.addMergedRegion(new CellRangeAddress(0, 0, 0, columns - 1));
            cell(
                    sh.createRow(1),
                    0,
                    "Proyecto: " + safe(p.projectName()) + "  ·  Código: " + safe(p.projectCode())
                            + "  ·  Año: " + p.year() + "  ·  Unidad: " + p.timeUnit() + "  ·  Períodos: "
                            + p.periodCount()
                            + (p.startDate() == null ? "" : "  ·  Inicio: " + p.startDate()),
                    null);
            String institution = Stream.of(p.institution(), p.subdirection())
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(" · "));
            cell(
                    sh.createRow(2),
                    0,
                    institution.isEmpty()
                            ? "Sistema APU — Cronograma Valorado de Trabajos"
                            : "Institución: " + safe(institution),
                    null);
            Row headings = sh.createRow(3);
            String[] fixed = {"Item", "Código", "Descripción", "Unidad", "Cantidad", "P.Unitario", "P.Total", "Peso"};
            for (int i = 0; i < fixed.length; i++) cell(headings, i, fixed[i], header);
            for (int i = 0; i < p.periodCount(); i++)
                cell(headings, 8 + i, ("SEMANA".equalsIgnoreCase(p.timeUnit()) ? "Semana " : "Mes ") + (i + 1), header);
            int index = 4, chapterIndex = 0;
            for (int i = 0; i <= p.rows().size(); i++) {
                while (chapterIndex < p.chapters().size()
                        && p.chapters().get(chapterIndex).beforeRow() <= i) {
                    var c = p.chapters().get(chapterIndex++);
                    Row row = sh.createRow(index++);
                    cell(row, 0, "Cap. " + safe(c.item()), chapter);
                    cell(row, 1, "", chapter);
                    cell(row, 2, safe(c.description()), chapter);
                    for (int col = 3; col < columns; col++) cell(row, col, "", chapter);
                    number(row, 6, c.total(), chapterMoney);
                }
                if (i == p.rows().size()) break;
                var source = p.rows().get(i);
                Row row = sh.createRow(index++);
                cell(row, 0, safe(source.item()), text);
                cell(row, 1, safe(source.code()), text);
                cell(row, 2, safe(source.description()), text);
                cell(row, 3, safe(source.unit()), text);
                number(row, 4, source.quantity(), money);
                number(row, 5, source.unitPrice(), money);
                number(row, 6, source.totalPrice(), money);
                number(row, 7, source.weight(), pct);
                for (int j = 0; j < p.periodCount(); j++) {
                    String value = source.percentByPeriod().get(j);
                    if (value == null) cell(row, 8 + j, "", text);
                    else number(row, 8 + j, value, pct);
                }
            }
            var totals = p.totals();
            summary(sh, index++, "% PARCIAL", totals.partialPercentage(), summary, summaryPct);
            summary(sh, index++, "% ACUMULADO", totals.accumulatedPercentage(), summary, summaryPct);
            summary(sh, index++, "MONTO PARCIAL", totals.partialAmount(), summary, summaryMoney);
            summary(sh, index, "MONTO ACUMULADO", totals.accumulatedAmount(), summary, summaryMoney);
            for (int i = 0; i < columns; i++) sh.autoSizeColumn(i);
            wb.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo generar el XLSX del cronograma", e);
        }
    }

    private static void summary(
            Sheet sheet, int index, String label, List<String> values, CellStyle style, CellStyle numberStyle) {
        Row row = sheet.createRow(index);
        cell(row, 0, label, style);
        for (int i = 0; i < values.size(); i++) number(row, i + 8, values.get(i), numberStyle);
    }

    private static void cell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        if (style != null) cell.setCellStyle(style);
    }

    private static void number(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellStyle(style);
        if (value == null) cell.setCellValue((String) null);
        else cell.setCellValue(new BigDecimal(value).doubleValue());
    }

    private static CellStyle numeric(Workbook wb, CellStyle base, int precision) {
        CellStyle style = wb.createCellStyle();
        style.cloneStyleFrom(base);
        style.setDataFormat(wb.createDataFormat().getFormat(precision <= 0 ? "0" : "0." + "0".repeat(precision)));
        return style;
    }

    private static void borders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private static String safe(String value) {
        if (value == null) return "";
        int i = 0;
        while (i < value.length() && value.charAt(i) <= ' ') i++;
        return i < value.length() && "=+-@".indexOf(value.charAt(i)) >= 0 ? "'" + value : value;
    }

    private static String sheetName(String code, String name) {
        String base = code == null || code.isBlank() ? name : code;
        if (base == null || base.isBlank()) base = "Cronograma";
        base = base.replaceAll("\\d{6,}", "")
                .replaceAll("[\\\\/:*?\\[\\]]", "_")
                .replace("..", "_");
        if (base.length() > 31) base = base.substring(0, 31);
        return base.isBlank() ? "Cronograma" : base;
    }
}
