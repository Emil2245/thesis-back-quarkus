package ec.uce.propuestas.cronograma.export;

import ec.uce.propuestas.cronograma.dto.ProyeccionExportacion;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Plan 031 (P-37) — writer XLSX del cronograma (Lane A documental).
 *
 * <p>Layout canónico:
 * <ul>
 *   <li>Cabecera institucional con título "CRONOGRAMA VALORADO DE TRABAJOS".</li>
 *   <li>Identidad del proyecto: código, nombre, año, unidad de tiempo, número
 *       de períodos, fecha de inicio (si existe).</li>
 *   <li>Columnas fijas: Item, Código, Descripción, Unidad, Cantidad,
 *       P.Unitario, P.Total, Peso.</li>
 *   <li>Una columna por período, etiquetada como "Semana N" o "Mes N".</li>
 *   <li>Filas de rubros ordenados en orden presupuestario.</li>
 *   <li>Cuatro filas de resumen: % PARCIAL, % ACUMULADO, MONTO PARCIAL,
 *       MONTO ACUMULADO.</li>
 * </ul>
 *
 * <p>Seguridad:
 * <ul>
 *   <li>Celda de texto que comience por {@code =}, {@code +}, {@code -} o
 *       {@code @} se neutraliza con prefijo {@code '} (estilo OWASP). Las
 *       celdas numéricas NO se neutralizan (son números reales).</li>
 *   <li>El nombre de la hoja se sanea: sin BIGINT (≥6 dígitos), sin
 *       {@code ..}, sin {@code /}, sin {@code \}. Longitud ≤ 31 (límite
 *       Excel). Caracteres no permitidos se reemplazan por {@code _}.</li>
 * </ul>
 */
public final class CronogramaXlsxWriter {

    private CronogramaXlsxWriter() {}

    /** Una fila de rubro en la hoja. */
    public record Fila(
            String item,
            String codigo,
            String descripcion,
            String unidad,
            BigDecimal cantidad,
            BigDecimal precioUnitario,
            BigDecimal precioTotal,
            BigDecimal peso) {}

    /**
     * Fila + periodos: el array {@code porcentajesPorPeriodo} (longitud = n)
     * lleva el porcentaje parcial por período (escala 4); el mapa
     * {@code montoPorPeriodo} lleva sólo los períodos activos (escala 6).
     */
    public record FilaHoja(
            Fila fila, List<BigDecimal> porcentajesPorPeriodo, Map<Integer, BigDecimal> montoPorPeriodo) {}

    /** Proyección completa que recibe el writer (una sola estructura inmutable). */
    public record ProyeccionXlsx(
            String proyectoCodigo,
            String proyectoNombre,
            int anio,
            String unidadTiempo,
            int numeroPeriodos,
            LocalDate fechaInicio,
            List<FilaHoja> filas) {}

    /**
     * Renderiza el XLSX consumiendo la proyeccion canonica. La hoja viene
     * de {@code proyeccion.hoja()} y los totales fila-a-fila de las listas
     * ya pre-computadas; el writer NO recalcula nada. Cumple el canon
     * "writers must consume ProyeccionExportacion values only".
     */
    public static byte[] renderizar(ProyeccionExportacion proyeccion) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet(sanearNombreHoja(proyeccion.hoja()));
            escribirLibro(wb, sh, proyeccion.hoja(), proyeccion);
            wb.write(baos);
            return baos.toByteArray();
        } catch (IOException ioe) {
            throw new RuntimeException("No se pudo generar el XLSX del cronograma", ioe);
        }
    }

    private static void escribirLibro(Workbook wb, Sheet sh, ProyeccionXlsx p, ProyeccionExportacion totalesResumen) {
        // Estilos reutilizables
        Font bold = wb.createFont();
        bold.setBold(true);

        CellStyle tituloEstilo = wb.createCellStyle();
        Font tituloFont = wb.createFont();
        tituloFont.setBold(true);
        tituloFont.setFontHeightInPoints((short) 16);
        tituloEstilo.setFont(tituloFont);
        tituloEstilo.setAlignment(HorizontalAlignment.CENTER);

        CellStyle cabeceraEstilo = wb.createCellStyle();
        cabeceraEstilo.setFont(bold);
        cabeceraEstilo.setAlignment(HorizontalAlignment.CENTER);
        cabeceraEstilo.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        cabeceraEstilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        aplicarBordes(cabeceraEstilo);

        CellStyle resumenEstilo = wb.createCellStyle();
        resumenEstilo.setFont(bold);
        resumenEstilo.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        resumenEstilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        aplicarBordes(resumenEstilo);

        CellStyle textoEstilo = wb.createCellStyle();
        aplicarBordes(textoEstilo);
        textoEstilo.setVerticalAlignment(VerticalAlignment.TOP);

        // (1) Título + identidad
        Row r0 = sh.createRow(0);
        crearCelda(r0, 0, neutralizar("CRONOGRAMA VALORADO DE TRABAJOS"), tituloEstilo);
        int columnas = 8 + p.numeroPeriodos();
        sh.addMergedRegion(new CellRangeAddress(0, 0, 0, columnas - 1));

        Row r1 = sh.createRow(1);
        crearCelda(
                r1,
                0,
                "Proyecto: " + neutralizar(proyeccionSegura(p.proyectoNombre)) + "  ·  Código: "
                        + neutralizar(proyeccionSegura(p.proyectoCodigo))
                        + "  ·  Año: " + p.anio
                        + "  ·  Unidad: " + p.unidadTiempo
                        + "  ·  Períodos: " + p.numeroPeriodos()
                        + (p.fechaInicio() != null ? "  ·  Inicio: " + p.fechaInicio : ""),
                null);

        Row r2 = sh.createRow(2);
        crearCelda(r2, 0, "Sistema APU — Cronograma Valorado de Trabajos", null);

        // (2) Cabecera de columnas
        Row r3 = sh.createRow(3);
        String[] fijas = {"Item", "Código", "Descripción", "Unidad", "Cantidad", "P.Unitario", "P.Total", "Peso"};
        for (int i = 0; i < fijas.length; i++) {
            crearCelda(r3, i, fijas[i], cabeceraEstilo);
        }
        String etiqueta = "SEMANA".equalsIgnoreCase(p.unidadTiempo) ? "Semana" : "Mes";
        for (int i = 0; i < p.numeroPeriodos(); i++) {
            crearCelda(r3, 8 + i, etiqueta + " " + (i + 1), cabeceraEstilo);
        }

        // (3) Filas de rubros
        int rowIdx = 4;
        for (FilaHoja fh : p.filas()) {
            Row r = sh.createRow(rowIdx++);
            crearCelda(r, 0, neutralizar(fh.fila().item()), textoEstilo);
            crearCelda(r, 1, neutralizar(fh.fila().codigo()), textoEstilo);
            crearCelda(r, 2, neutralizar(fh.fila().descripcion()), textoEstilo);
            crearCelda(r, 3, neutralizar(fh.fila().unidad()), textoEstilo);
            crearCeldaNumerica(r, 4, fh.fila().cantidad());
            crearCeldaNumerica(r, 5, fh.fila().precioUnitario());
            crearCeldaNumerica(r, 6, fh.fila().precioTotal());
            crearCeldaNumerica(r, 7, fh.fila().peso());
            // Períodos: porcentaje parcial (escala 4). Celda vacía si la
            // columna no es activa; cero numérico si la columna es activa con
            // valor 0.
            for (int i = 0; i < p.numeroPeriodos(); i++) {
                int periodo = i + 1;
                BigDecimal pct = i < fh.porcentajesPorPeriodo().size()
                        ? fh.porcentajesPorPeriodo().get(i)
                        : null;
                if (pct == null) {
                    crearCelda(r, 8 + i, "", textoEstilo);
                } else {
                    crearCeldaNumerica(r, 8 + i, pct);
                }
            }
        }

        // (4) Cuatro filas de resumen: % PARCIAL, % ACUMULADO, MONTO PARCIAL,
        //     MONTO ACUMULADO. Los totales llegan pre-computados en
        //     {@code totalesResumen} (ProyeccionExportacion) -- el writer NO
        //     recalcula; cumple el canon "writers must consume
        //     ProyeccionExportacion values only".
        ProyeccionExportacion totales = totalesResumen;
        BigDecimal[] parcialPct = totales.parcialPorcentaje().toArray(new BigDecimal[0]);
        BigDecimal[] acumPct = totales.acumuladoPorcentaje().toArray(new BigDecimal[0]);
        BigDecimal[] parcialMonto = totales.parcialMonto().toArray(new BigDecimal[0]);
        BigDecimal[] acumMonto = totales.acumuladoMonto().toArray(new BigDecimal[0]);

        rowIdx = escribirResumen(sh, rowIdx, "% PARCIAL", parcialPct, resumenEstilo);
        rowIdx = escribirResumen(sh, rowIdx, "% ACUMULADO", acumPct, resumenEstilo);
        rowIdx = escribirResumen(sh, rowIdx, "MONTO PARCIAL", parcialMonto, resumenEstilo);
        escribirResumen(sh, rowIdx, "MONTO ACUMULADO", acumMonto, resumenEstilo);

        // Auto-anchos razonables.
        for (int i = 0; i < columnas; i++) {
            sh.autoSizeColumn(i);
        }
    }

    private static int escribirResumen(Sheet sh, int rowIdx, String etiqueta, BigDecimal[] valores, CellStyle estilo) {
        Row r = sh.createRow(rowIdx);
        crearCelda(r, 0, etiqueta, estilo);
        for (int i = 0; i < valores.length; i++) {
            crearCeldaNumerica(r, 8 + i, valores[i]);
        }
        return rowIdx + 1;
    }

    private static void crearCelda(Row fila, int col, String texto, CellStyle estilo) {
        Cell c = fila.createCell(col);
        c.setCellValue(texto);
        if (estilo != null) {
            c.setCellStyle(estilo);
        }
    }

    private static void crearCeldaNumerica(Row fila, int col, BigDecimal valor) {
        Cell c = fila.createCell(col);
        if (valor == null) {
            c.setCellValue((String) null);
            return;
        }
        c.setCellValue(valor.doubleValue());
    }

    private static void aplicarBordes(CellStyle estilo) {
        estilo.setBorderTop(BorderStyle.THIN);
        estilo.setBorderBottom(BorderStyle.THIN);
        estilo.setBorderLeft(BorderStyle.THIN);
        estilo.setBorderRight(BorderStyle.THIN);
    }

    /**
     * Neutraliza formula injection siguiendo la guia canonica (OWASP).
     *
     * <p>Si la cadena empieza por un prefijo peligroso ({@code =},
     * {@code +}, {@code -} o {@code @}) precedido SOLO por whitespace
     * insignificante (espacios ASCII, tabs, CR, LF, control chars
     * {@code <= 0x20}), antepone {@code '} para que Excel la trate como
     * texto plano en lugar de formula. Los attackers usan precisamente
     * secuencias como {@code " =cmd"} o {@code "\t=cmd"} que Excel
     * interpreta como formula al hacer trim implicito; este helper
     * blindea ese vector. Caso contrario, devuelve la cadena intacta.
     *
     * <p>Se preserva el texto original completo (incluyendo el whitespace
     * inicial) para no destruir formato visible al usuario.</p>
     */
    public static String neutralizar(String texto) {
        if (texto == null) {
            return "";
        }
        if (texto.isEmpty()) {
            return texto;
        }
        int idx = 0;
        int len = texto.length();
        // Saltar whitespace insignificante (espacio, tab, CR, LF y demas
        // control chars <= 0x20) -- todos los que Excel/POI trimean al
        // evaluar formulas. NBSP (\u00A0) NO se considera whitespace
        // insignificante para Excel (Excel NO trimea NBSP) por lo que
        // NO se neutraliza un NBSP seguido de '='.
        while (idx < len) {
            char c = texto.charAt(idx);
            if (c > ' ') {
                break;
            }
            idx++;
        }
        if (idx >= len) {
            return texto;
        }
        char primero = texto.charAt(idx);
        if (primero == '=' || primero == '+' || primero == '-' || primero == '@') {
            return "'" + texto;
        }
        return texto;
    }

    /**
     * Sanea el nombre de la hoja: sin caracteres prohibidos por Excel
     * ({@code : \\ / ? * [ ]}), sin path traversal ({@code ..}), sin
     * secuencias de BIGINT (≥ 6 dígitos consecutivos), longitud ≤ 31.
     */
    public static String sanearNombreHoja(ProyeccionXlsx p) {
        String base = p.proyectoCodigo();
        if (base == null || base.isBlank()) {
            base = p.proyectoNombre();
        }
        if (base == null || base.isBlank()) {
            base = "Cronograma";
        }
        // Eliminar secuencias de BIGINT (≥ 6 dígitos consecutivos).
        base = base.replaceAll("\\d{6,}", "");
        // Reemplazar caracteres prohibidos por "_".
        base = base.replaceAll("[\\\\/:*?\\[\\]]", "_");
        // Quitar dobles puntos.
        base = base.replace("..", "_");
        // Truncar a 31.
        if (base.length() > 31) {
            base = base.substring(0, 31);
        }
        if (base.isBlank()) {
            base = "Cronograma";
        }
        return base;
    }

    private static String proyeccionSegura(String s) {
        return s == null ? "" : s;
    }
}
