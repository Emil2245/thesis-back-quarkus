package ec.uce.propuestas.documentos.render;

import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.Capitulo;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.Firmante;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.Snapshot;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STCellType;

/** Presents projected budget values without accessing or recalculating domain data. */
public final class PresupuestoXlsxRenderer {
    private static final String[] CABECERAS = {
        "Ítem", "Código", "Descripción", "Unidad", "Cantidad", "P.Unitario", "P.Total"
    };

    private PresupuestoXlsxRenderer() {}

    public static byte[] renderizar(DocumentProjectionV1 projection) {
        Snapshot snapshot = Objects.requireNonNull(projection).snapshot();
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
                        if (value != null) numero(cell, value);
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
            numero(importe, snapshot.total());
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

    private static void numero(XSSFCell cell, BigDecimal value) {
        // Preserve the projected decimal and scale in OOXML rather than converting through double.
        cell.getCTCell().setT(STCellType.N);
        cell.getCTCell().setV(value.toPlainString());
    }

    private static void texto(XSSFCell cell, String value, CellStyle style) {
        cell.setCellValue(seguro(value));
        cell.setCellStyle(style);
    }

    private static float altura(String value, int width) {
        int lines = 0;
        for (String line : seguro(value).split("\n", -1)) lines += Math.max(1, (line.length() + width - 1) / width);
        return Math.min(409, Math.max(24, lines * 15));
    }

    private static String seguro(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Cc}&&[^\\n\\t]]", "");
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
        return List.copyOf(result);
    }

    private static List<String> cierre(Snapshot s) {
        var result = new ArrayList<String>();
        result.add("Nota: los precios no incluyen IVA");
        if (s.notas() != null && !s.notas().isBlank()) result.add(seguro(s.notas()));
        var porRol = new LinkedHashMap<String, List<Firmante>>();
        for (var rol : List.of("CONSOLIDADO", "APROBADO")) porRol.put(rol, new ArrayList<>());
        s.firmantes().stream()
                .sorted(Comparator.comparingInt(Firmante::orden).thenComparing(f -> f.id().toString()))
                .forEach(f ->
                        porRol.computeIfAbsent(f.rol(), r -> new ArrayList<>()).add(f));
        porRol.forEach((rol, firmantes) -> {
            if (firmantes.isEmpty()) return;
            boolean conocido = rol.equals("CONSOLIDADO") || rol.equals("APROBADO");
            result.add(seguro(rol) + (conocido ? " POR:" : ":"));
            for (var firma : firmantes) {
                result.add(seguro(firma.nombre()) + "\n" + seguro(firma.cargo()));
                result.add("FIRMA:");
            }
        });
        if (s.parametros().mensajeFooter() != null
                && !s.parametros().mensajeFooter().isBlank())
            result.add(seguro(s.parametros().mensajeFooter()));
        return List.copyOf(result);
    }

    private record Fila(boolean capitulo, List<String> textos, List<BigDecimal> numeros) {}

    private static List<Fila> filasPresentacion(Snapshot s) {
        var filas = new ArrayList<Fila>();
        for (var capitulo : s.capitulos()) recorrer(capitulo, filas);
        var result = new ArrayList<Fila>();
        for (Fila fila : filas) {
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
                        i == 0 ? fila.numeros() : Arrays.asList(null, null, null)));
            }
        }
        return List.copyOf(result);
    }

    private static List<String> fragmentos(String text) {
        if (text.length() <= 32767 && altura(text, 30) <= 240) return List.of(text);
        var result = new ArrayList<String>();
        int start = 0;
        while (start < text.length()) {
            int end = start;
            int lines = 1;
            int columns = 0;
            int boundary = start;
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

    private static void recorrer(Capitulo c, List<Fila> result) {
        result.add(new Fila(
                true,
                List.of(seguro(c.item()), "", seguro(c.descripcion()), ""),
                Arrays.asList(null, null, c.total())));
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
