package ec.uce.propuestas.documento;

import ec.uce.propuestas.documento.exportacion.OpcionesDocumento;
import ec.uce.propuestas.documento.exportacion.SnapshotDocumento;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STCellType;

/** Serialización pura: no consulta ni calcula importes del dominio. */
public final class PresupuestoXlsxWriter {
    static final String[] CABECERAS = {"Ítem", "Código", "Descripción", "Unidad", "Cantidad", "P.Unitario", "P.Total"};
    static final String NOTA_IVA = "Los precios indicados no incluyen IVA (sin IVA).";

    private PresupuestoXlsxWriter() {}

    public static byte[] renderizar(SnapshotDocumento snapshot, OpcionesDocumento opciones) {
        java.util.Objects.requireNonNull(opciones);
        try (var wb = new XSSFWorkbook();
                var out = new ByteArrayOutputStream()) {
            var sh = wb.createSheet("Presupuesto");
            CellStyle texto = estilo(wb, false, false, snapshot.display().precision());
            CellStyle numero = estilo(wb, false, true, snapshot.display().precision());
            CellStyle titulo = estilo(wb, true, false, snapshot.display().precision());
            CellStyle total = estilo(wb, true, true, snapshot.display().precision());
            int rowIndex = 0;
            for (String linea : identificacion(snapshot)) {
                var row = sh.createRow(rowIndex++);
                texto(row.createCell(0), linea, titulo);
                sh.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, 6));
                row.setHeightInPoints(altura(linea, 110));
            }
            int header = rowIndex;
            var cabecera = sh.createRow(rowIndex++);
            for (int c = 0; c < 7; c++) texto(cabecera.createCell(c), CABECERAS[c], titulo);
            cabecera.setHeightInPoints(30);
            for (Fila fila : filasPresentacion(snapshot)) {
                var row = sh.createRow(rowIndex++);
                for (int c = 0; c < 7; c++) {
                    XSSFCell cell = row.createCell(c);
                    if (c < 4) {
                        String value = fila.textos().get(c);
                        if (value.isEmpty()) cell.setCellStyle(fila.capitulo() ? titulo : texto);
                        else texto(cell, value, fila.capitulo() ? titulo : texto);
                    } else {
                        BigDecimal value = fila.numeros().get(c - 4);
                        cell.setCellStyle(fila.capitulo() ? total : numero);
                        if (value != null) {
                            // POI's double setter loses stored precision. Write exact numeric OOXML,
                            // keeping the source scale and using formats only for presentation.
                            cell.getCTCell().setT(STCellType.N);
                            cell.getCTCell().setV(value.toPlainString());
                        }
                    }
                }
                float height = 24;
                int[] readableWidths = {10, 14, 30, 8};
                for (int c = 0; c < 4; c++)
                    height = Math.max(height, altura(fila.textos().get(c), readableWidths[c]));
                row.setHeightInPoints(height);
            }
            var cierre = sh.createRow(rowIndex++);
            texto(cierre.createCell(0), "TOTAL", titulo);
            XSSFCell importe = cierre.createCell(6);
            importe.setCellStyle(total);
            importe.getCTCell().setT(STCellType.N);
            importe.getCTCell().setV(snapshot.total().toPlainString());
            for (String linea : cierre(snapshot)) {
                var row = sh.createRow(rowIndex++);
                texto(row.createCell(0), linea, texto);
                sh.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, 6));
                row.setHeightInPoints(altura(linea, 110));
            }
            int[] widths = {12, 18, 44, 10, 20, 20, 20};
            for (int c = 0; c < 7; c++) sh.setColumnWidth(c, widths[c] * 256);
            sh.setRepeatingRows(new CellRangeAddress(header, header, -1, -1));
            sh.createFreezePane(0, header + 1);
            sh.getPrintSetup().setPaperSize(org.apache.poi.ss.usermodel.PrintSetup.A4_PAPERSIZE);
            sh.getPrintSetup().setFitWidth((short) 1);
            sh.getPrintSetup().setFitHeight((short) 0);
            sh.setFitToPage(true);
            wb.setPrintArea(0, 0, 6, 0, rowIndex - 1);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el XLSX del presupuesto", e);
        }
    }

    private static CellStyle estilo(XSSFWorkbook wb, boolean bold, boolean numeric, int precision) {
        var style = wb.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        if (bold) {
            var font = wb.createFont();
            font.setBold(true);
            style.setFont(font);
        }
        if (numeric) {
            style.setAlignment(HorizontalAlignment.RIGHT);
            style.setDataFormat(wb.createDataFormat().getFormat(precision == 0 ? "0" : "0." + "0".repeat(precision)));
        }
        return style;
    }

    private static void texto(XSSFCell cell, String value, CellStyle style) {
        // Explicit STRING cells are inert even for leading =, +, -, @. No formula parsing.
        cell.setCellValue(seguro(value));
        cell.setCellStyle(style);
    }

    private static float altura(String value, int width) {
        int lines = 0;
        for (String line : seguro(value).split("\n", -1)) lines += Math.max(1, (line.length() + width - 1) / width);
        return Math.min(409, Math.max(24, lines * 15));
    }

    static String seguro(String value) {
        if (value == null) return "";
        // Drop non-printable controls, retaining multiline text; never interpret text as markup.
        return value.replaceAll("[\\p{Cc}&&[^\\n\\t]]", "");
    }

    static String visible(BigDecimal value, int precision) {
        return value == null
                ? ""
                : value.setScale(precision, RoundingMode.HALF_UP).toPlainString();
    }

    static List<String> identificacion(SnapshotDocumento s) {
        var result = new ArrayList<String>();
        result.add("PRESUPUESTO");
        if (s.proyecto().direccionInstitucional() != null
                && !s.proyecto().direccionInstitucional().isBlank())
            result.add(seguro(s.proyecto().direccionInstitucional()));
        if (s.proyecto().subdireccionInstitucional() != null
                && !s.proyecto().subdireccionInstitucional().isBlank())
            result.add(seguro(s.proyecto().subdireccionInstitucional()));
        result.add("Proyecto: " + seguro(s.proyecto().nombre()) + " · Código: "
                + seguro(s.proyecto().codigo()));
        result.add("Año: " + s.proyecto().anio() + " · Versión: " + s.version());
        result.add("Presupuesto: " + s.presupuestoId());
        return List.copyOf(result);
    }

    static List<String> cierre(SnapshotDocumento s) {
        var result = new ArrayList<String>();
        result.add(NOTA_IVA);
        if (s.notas() != null && !s.notas().isBlank()) result.add(seguro(s.notas()));
        s.firmantes().stream()
                .sorted(Comparator.comparingInt(SnapshotDocumento.Firmante::orden)
                        .thenComparing(f -> f.id().toString()))
                .forEach(f -> result.add(seguro(f.nombre()) + " · " + seguro(f.cargo()) + " · " + seguro(f.rol())));
        if (s.parametros().mensajeFooter() != null
                && !s.parametros().mensajeFooter().isBlank())
            result.add(seguro(s.parametros().mensajeFooter()));
        return List.copyOf(result);
    }

    record Fila(boolean capitulo, List<String> textos, List<BigDecimal> numeros) {}

    /** Bounded physical rows shared by both readers; financial rows remain untouched. */
    static List<Fila> filasPresentacion(SnapshotDocumento s) {
        var result = new ArrayList<Fila>();
        for (Fila fila : filas(s)) {
            List<String> chunks = fragmentos(fila.textos().get(2));
            for (int i = 0; i < chunks.size(); i++) {
                result.add(new Fila(
                        fila.capitulo(),
                        i == 0
                                ? List.of(
                                        fila.textos().get(0),
                                        fila.textos().get(1),
                                        chunks.get(i),
                                        fila.textos().get(3))
                                : List.of("(continuación)", "", chunks.get(i), ""),
                        i == 0 ? fila.numeros() : java.util.Arrays.asList(null, null, null)));
            }
        }
        return List.copyOf(result);
    }

    static List<String> fragmentos(String text) {
        // Ordinary rows retain their established structure; only oversized text expands.
        if (text.length() <= 32767 && altura(text, 30) <= 240) return List.of(text);
        var result = new ArrayList<String>();
        int start = 0;
        while (start < text.length()) {
            int end = start;
            int lines = 1;
            int columns = 0;
            int boundary = start;
            // At most ten conservative 30-character lines: safely below a Calc row
            // limit and a landscape PDF page, including word-wrap and cell padding.
            while (end < text.length()) {
                int cp = text.codePointAt(end);
                int nextLines = lines;
                int nextColumns = columns + (cp == '\t' ? 4 : 1);
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
        return result.isEmpty() ? List.of("") : List.copyOf(result);
    }

    static List<Fila> filas(SnapshotDocumento s) {
        var result = new ArrayList<Fila>();
        for (var capitulo : s.capitulos()) recorrer(capitulo, result);
        return List.copyOf(result);
    }

    private static void recorrer(SnapshotDocumento.Capitulo c, List<Fila> result) {
        result.add(new Fila(
                true,
                List.of(seguro(c.item()), "", seguro(c.descripcion()), ""),
                java.util.Arrays.asList(null, null, c.total())));
        for (var hijo : c.hijos()) recorrer(hijo, result);
        c.rubros().stream()
                .sorted((a, b) -> {
                    int order = compararItem(a.item(), b.item());
                    return order != 0 ? order : a.id().toString().compareTo(b.id().toString());
                })
                .forEach(r -> result.add(new Fila(
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
}
