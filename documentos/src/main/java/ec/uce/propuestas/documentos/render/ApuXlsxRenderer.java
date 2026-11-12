package ec.uce.propuestas.documentos.render;

import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STCellType;

/** Serializes Core's ordered, render-ready APU projection without calculating amounts. */
public final class ApuXlsxRenderer {
    private ApuXlsxRenderer() {}

    public static byte[] renderizar(DocumentProjectionV1 projection) {
        if (!"apus".equals(projection.documento())
                || !"xlsx".equals(projection.formato())
                || !projection.opciones().keySet().equals(Set.of("layout"))
                || !Set.of("pestanas", "apilado").contains(projection.opciones().get("layout"))) {
            throw new IllegalArgumentException("Opciones incompatibles con APU XLSX");
        }
        try (var wb = new XSSFWorkbook();
                var out = new ByteArrayOutputStream()) {
            var snapshot = projection.snapshot();
            var styles = new Styles(wb, snapshot.display());
            boolean stacked = "apilado".equals(projection.opciones().get("layout"));
            XSSFSheet common = stacked && !snapshot.apuRender().isEmpty() ? wb.createSheet("APUs") : null;
            var used = new HashSet<String>();
            int offset = 0;
            for (var apu : snapshot.apuRender()) {
                var sheet = stacked ? common : wb.createSheet(sheetName(apu, used));
                if (stacked && offset > 0) sheet.setRowBreak(offset - 1);
                var cursor = new Cursor(sheet, stacked ? offset : 0, styles, apu);
                block(cursor, snapshot, apu);
                cursor.flush();
                configure(wb, sheet, cursor.index);
                offset = cursor.index;
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el XLSX de APUs", e);
        }
    }

    private static void block(Cursor c, Snapshot snapshot, ApuView apu) {
        c.line("ANÁLISIS DE PRECIOS UNITARIOS", true);
        c.line(snapshot.proyecto().direccionInstitucional(), false);
        c.line(snapshot.proyecto().subdireccionInstitucional(), false);
        c.line("PROYECTO: " + snapshot.proyecto().nombre(), true);
        c.line("Año: " + snapshot.proyecto().anio(), false);
        if (apu.nombreProyectoHeader() != null) c.line("Proyecto: " + apu.nombreProyectoHeader(), false);
        c.line("Código: " + apu.etiquetaCodigo(), true);
        c.line(apu.descripcion(), false);
        c.line("Unidad: " + apu.unidad(), false);
        for (var section : apu.bloques()) {
            boolean hourly = section.tipo() == SeccionTipo.EQUIPO || section.tipo() == SeccionTipo.MANO_OBRA;
            c.section(section.etiqueta(), headers(section.tipo()));
            for (var projected : section.filas()) {
                var detail = projected.detalle();
                var descriptions = chunks(projected.descripcion());
                var units = chunks(detail.unidad());
                int count = Math.max(descriptions.size(), hourly ? 0 : units.size());
                for (int i = 0; i < count; i++) {
                    var row = c.next();
                    c.text(row, 0, i < descriptions.size() ? descriptions.get(i) : "", false);
                    if (!hourly) c.text(row, 1, i < units.size() ? units.get(i) : "", false);
                    if (i != 0) continue;
                    if (hourly) {
                        c.number(row, 1, detail.cantidad(), c.styles.effective);
                        c.number(row, 2, detail.precioEfectivo(), c.styles.money);
                        c.number(row, 3, projected.costoHora(), c.styles.money);
                        c.number(row, 4, detail.rendimiento(), c.styles.effective);
                    } else {
                        c.number(row, 2, detail.cantidad(), c.styles.effective);
                        c.number(row, 3, detail.precioEfectivo(), c.styles.money);
                    }
                    c.number(row, 5, projected.costo(), c.styles.money);
                }
            }
            if (section.mostrarSubtotal()) c.amount("Subtotal " + section.etiqueta(), section.subtotal());
            c.endSection();
        }
        var footer = apu.pie();
        for (var subtotal : footer.subtotales()) c.amount("Subtotal " + subtotal.tipo(), subtotal.valor());
        c.amount("Costo directo", footer.costoDirecto());
        var indirect = c.next();
        c.text(indirect, 0, "Costo indirecto", true);
        c.number(indirect, 4, footer.porcentajeIndirecto(), c.styles.percent);
        c.number(indirect, 5, footer.costoIndirecto(), c.styles.money);
        c.amount("Costo total", footer.costoTotal());
        c.amount("Valor ofertado", footer.valorOfertado());
        c.line(footer.mensaje(), false);
        signatures(c, snapshot.firmantes());
    }

    private static List<String> headers(SeccionTipo type) {
        return switch (type) {
            case EQUIPO -> List.of("Descripción", "Cantidad", "Tarifa", "Costo hora", "Rendimiento", "Costo");
            case MANO_OBRA -> List.of("Descripción", "Cantidad", "Jornal/hr", "Costo hora", "Rendimiento", "Costo");
            case MATERIAL -> List.of("Descripción", "Unidad", "Cantidad", "Precio unitario", "", "Costo");
            case TRANSPORTE -> List.of("Descripción", "Unidad", "Cantidad", "Tarifa", "", "Costo");
        };
    }

    private static void signatures(Cursor c, List<Firmante> signers) {
        Map<String, List<Firmante>> groups = new LinkedHashMap<>();
        groups.put("CONSOLIDADO", new ArrayList<>());
        groups.put("APROBADO", new ArrayList<>());
        signers.stream()
                .sorted(Comparator.comparingInt(Firmante::orden).thenComparing(f -> f.id().toString()))
                .forEach(f -> groups.computeIfAbsent(f.rol(), ignored -> new ArrayList<>())
                        .add(f));
        groups.forEach((role, values) -> {
            if (values.isEmpty()) return;
            c.line(safe(role) + (role.equals("CONSOLIDADO") || role.equals("APROBADO") ? " POR:" : ":"), true);
            for (var signer : values) {
                c.line(safe(signer.nombre()) + "\n" + safe(signer.cargo()), false);
                c.line("FIRMA:", false);
            }
        });
    }

    private static String sheetName(ApuView apu, Set<String> used) {
        String base = safe(apu.codigo())
                .replaceAll("[\\[\\]:*?/\\\\\\p{Cc}]", " ")
                .strip()
                .replaceAll("^'+|'+$", "")
                .strip();
        if (base.isEmpty()) base = "APU";
        base = cut(base, 31).replaceAll("'+$", "");
        String name = base;
        int attempt = 0;
        while (name.equalsIgnoreCase("History") || !used.add(name.toLowerCase(Locale.ROOT))) {
            String suffix = "-" + apu.id().toString().replace("-", "").substring(0, 8) + "-" + (++attempt);
            name = cut(base, 31 - suffix.length()) + suffix;
        }
        return name;
    }

    private static String cut(String text, int max) {
        int end = Math.min(text.length(), max);
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }

    private static void configure(XSSFWorkbook wb, XSSFSheet sheet, int rows) {
        int[] widths = {44, 14, 20, 20, 20, 20};
        for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
        sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
        sheet.getPrintSetup().setLandscape(false);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
        sheet.setFitToPage(true);
        sheet.setMargin(Sheet.TopMargin, 0.5);
        sheet.setMargin(Sheet.BottomMargin, 0.5);
        sheet.setMargin(Sheet.LeftMargin, 0.4);
        sheet.setMargin(Sheet.RightMargin, 0.4);
        wb.setPrintArea(wb.getSheetIndex(sheet), 0, 5, 0, rows - 1);
    }

    private static String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Cc}&&[^\\n\\t]]", "");
    }

    private static List<String> chunks(String value) {
        String text = safe(value);
        int height = 0;
        for (String line : text.split("\n", -1)) height += Math.max(1, (line.length() + 29) / 30);
        if (text.length() <= 32767 && Math.max(24, height * 15) <= 240) return List.of(text);
        var result = new ArrayList<String>();
        int start = 0;
        while (start < text.length()) {
            int end = start, lines = 1, columns = 0, boundary = start;
            while (end < text.length()) {
                int cp = text.codePointAt(end), nextLines = lines, nextColumns = columns + (cp == '\t' ? 4 : 1);
                if (cp == '\n') {
                    nextLines++;
                    nextColumns = 0;
                } else if (nextColumns > 30) {
                    nextLines++;
                    nextColumns = 1;
                }
                if (nextLines > 10) break;
                end += Character.charCount(cp);
                lines = nextLines;
                columns = nextColumns;
                if (Character.isWhitespace(cp)) boundary = end;
            }
            if (end < text.length() && boundary > start) end = boundary;
            result.add(text.substring(start, end));
            start = end;
        }
        return result.isEmpty() ? List.of("") : result;
    }

    private static final class Styles {
        final CellStyle text, title, money, percent, effective;

        Styles(XSSFWorkbook wb, Display display) {
            text = wb.createCellStyle();
            text.setWrapText(true);
            text.setVerticalAlignment(VerticalAlignment.TOP);
            title = wb.createCellStyle();
            title.cloneStyleFrom(text);
            var font = wb.createFont();
            font.setBold(true);
            title.setFont(font);
            money = numeric(wb, format(display.precision()));
            percent = numeric(wb, format(display.precisionPorcentaje()) + "%");
            effective = numeric(wb, "0.##############################");
        }

        private static String format(int precision) {
            return "0" + (precision == 0 ? "" : "." + "0".repeat(precision));
        }

        private CellStyle numeric(XSSFWorkbook wb, String format) {
            var style = wb.createCellStyle();
            style.cloneStyleFrom(text);
            style.setDataFormat(wb.createDataFormat().getFormat(format));
            return style;
        }
    }

    private record Cell(String text, BigDecimal number, CellStyle style) {}

    private static final class Row {
        final Map<Integer, Cell> cells = new TreeMap<>();
        float height = 24;
        boolean merged;
    }

    private static final class Cursor {
        final XSSFSheet sheet;
        final Styles styles;
        final List<String> identity;
        int index;
        float pageHeight;
        Row pending;
        List<Row> headings = List.of();
        boolean first;

        Cursor(XSSFSheet sheet, int index, Styles styles, ApuView apu) {
            this.sheet = sheet;
            this.index = index;
            this.styles = styles;
            identity = List.of(
                    "APU UUID: " + apu.id(),
                    "APU código: " + preview(apu.etiquetaCodigo()),
                    "APU descripción: " + preview(apu.descripcion()));
            pageHeader();
        }

        Row next() {
            flush();
            pending = new Row();
            return pending;
        }

        void flush() {
            if (pending == null) return;
            boolean pageBreak = pageHeight + pending.height + (first ? headingHeight() : 0) > 650;
            if (pageBreak) {
                sheet.setRowBreak(index - 1);
                pageHeight = 0;
                pageHeader();
            }
            if (first || pageBreak) headings.forEach(this::write);
            first = false;
            write(pending);
            pending = null;
        }

        float headingHeight() {
            float sum = 0;
            for (var row : headings) sum += row.height;
            return sum;
        }

        void section(String label, List<String> columns) {
            flush();
            var title = new Row();
            title.merged = true;
            text(title, 0, label, true);
            var schema = new Row();
            schema.height = 30;
            for (int i = 0; i < columns.size(); i++) text(schema, i, columns.get(i), true);
            headings = List.of(title, schema);
            first = true;
        }

        void endSection() {
            flush();
            if (first) {
                if (pageHeight + headingHeight() > 650) {
                    sheet.setRowBreak(index - 1);
                    pageHeight = 0;
                    pageHeader();
                }
                headings.forEach(this::write);
            }
            headings = List.of();
            first = false;
        }

        void pageHeader() {
            for (String value : identity) {
                var row = new Row();
                row.merged = true;
                row.height = 30;
                row.cells.put(0, new Cell(value, null, styles.title));
                write(row);
            }
        }

        static String preview(String value) {
            String single = value == null ? "" : value.replaceAll("\\s+", " ").strip();
            return single.length() <= 90 ? single : cut(single, 89) + "…";
        }

        void write(Row source) {
            if (index >= 1_048_576) throw new IllegalArgumentException("APU excede límite de filas XLSX");
            var row = sheet.createRow(index++);
            row.setHeightInPoints(source.height);
            for (var entry : source.cells.entrySet()) {
                var cell = row.createCell(entry.getKey());
                var value = entry.getValue();
                cell.setCellStyle(value.style());
                if (value.number() == null) cell.setCellValue(value.text());
                else {
                    cell.getCTCell().setT(STCellType.N);
                    cell.getCTCell().setV(value.number().toPlainString());
                }
            }
            if (source.merged)
                sheet.addMergedRegionUnsafe(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, 5));
            pageHeight += source.height;
        }

        void text(Row row, int col, String value, boolean bold) {
            row.cells.put(col, new Cell(value, null, bold ? styles.title : styles.text));
            int lines = 0;
            for (String line : value.split("\n", -1))
                lines += Math.max(1, (line.codePointCount(0, line.length()) + 29) / 30);
            row.height = Math.max(row.height, Math.min(240, lines * 15 + 15));
        }

        void number(Row row, int col, BigDecimal value, CellStyle style) {
            if (value != null) row.cells.put(col, new Cell(null, value, style));
        }

        void line(String value, boolean bold) {
            if (value == null || value.isBlank()) return;
            for (String chunk : chunks(value)) {
                var row = next();
                text(row, 0, chunk, bold);
                row.merged = true;
            }
        }

        void amount(String label, BigDecimal value) {
            var row = next();
            row.height = 30;
            text(row, 0, label, true);
            number(row, 5, value, styles.money);
        }
    }
}
