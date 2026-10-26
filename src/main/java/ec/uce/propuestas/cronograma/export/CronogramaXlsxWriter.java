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

    /**
     * Fila de capítulo/subcapítulo: agrupación sin datos de actividad (CHK-22). {@code antesDeFila} es el
     * índice, dentro de {@code filas}, de la primera fila de rubro que se imprime después de este capítulo.
     */
    public record CapituloHoja(String item, String descripcion, BigDecimal total, int antesDeFila) {}

    /** Proyección completa que recibe el writer (una sola estructura inmutable). */
    public record ProyeccionXlsx(
            String proyectoCodigo,
            String proyectoNombre,
            int anio,
            String unidadTiempo,
            int numeroPeriodos,
            LocalDate fechaInicio,
            List<FilaHoja> filas,
            List<CapituloHoja> capitulos,
            String institucion,
            String subdireccion,
            int precisionDinero,
            int precisionPorcentaje) {
        public ProyeccionXlsx {
            capitulos = capitulos == null ? List.of() : List.copyOf(capitulos);
            institucion = institucion == null ? "" : institucion;
            subdireccion = subdireccion == null ? "" : subdireccion;
        }

        /** Sin capítulos, institución ni configuración de display: precisiones por defecto (2 y 4). */
        public ProyeccionXlsx(
                String proyectoCodigo,
                String proyectoNombre,
                int anio,
                String unidadTiempo,
                int numeroPeriodos,
                LocalDate fechaInicio,
                List<FilaHoja> filas) {
            this(
                    proyectoCodigo,
                    proyectoNombre,
                    anio,
                    unidadTiempo,
                    numeroPeriodos,
                    fechaInicio,
                    filas,
                    List.of(),
                    "",
                    "",
                    2,
                    4);
        }

        /** Texto de la fila de institución del encabezado, vacío si el proyecto no la tiene. */
        public String institucionVisible() {
            return java.util.stream.Stream.of(institucion, subdireccion)
                    .filter(s -> !s.isBlank())
                    .collect(java.util.stream.Collectors.joining(" · "));
        }

        /** {@code valor} a {@code precisionDinero} dp (HALF_UP), solo presentación. */
        public String dinero(BigDecimal valor) {
            return valor == null
                    ? ""
                    : valor.setScale(precisionDinero, java.math.RoundingMode.HALF_UP)
                            .toPlainString();
        }

        /** {@code valor} (ya en unidades de porcentaje) a {@code precisionPorcentaje} dp (HALF_UP). */
        public String porcentaje(BigDecimal valor) {
            return valor == null
                    ? ""
                    : valor.setScale(precisionPorcentaje, java.math.RoundingMode.HALF_UP)
                            .toPlainString();
        }
    }

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

        // Formatos de presentación (CHK-28): dinero a precisionDinero dp y porcentaje a precisionPorcentaje dp.
        // El valor de la celda no cambia; solo cómo se muestra.
        String fmtDinero = formato(p.precisionDinero());
        String fmtPct = formato(p.precisionPorcentaje());
        CellStyle dineroEstilo = numerico(wb, textoEstilo, fmtDinero);
        CellStyle pctEstilo = numerico(wb, textoEstilo, fmtPct);
        CellStyle resumenDinero = numerico(wb, resumenEstilo, fmtDinero);
        CellStyle resumenPct = numerico(wb, resumenEstilo, fmtPct);
        CellStyle capituloEstilo = wb.createCellStyle();
        capituloEstilo.setFont(bold);
        capituloEstilo.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        capituloEstilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        aplicarBordes(capituloEstilo);
        CellStyle capituloDinero = numerico(wb, capituloEstilo, fmtDinero);

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

        // Institución del proyecto (spec §4.2 «Identidad»); el modelo no tiene campo de ubicación.
        Row r2 = sh.createRow(2);
        String institucion = p.institucionVisible();
        crearCelda(
                r2,
                0,
                institucion.isEmpty()
                        ? "Sistema APU — Cronograma Valorado de Trabajos"
                        : "Institución: " + neutralizar(proyeccionSegura(institucion)),
                null);

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

        // (3) Filas de capítulo (agrupación, CHK-22) y de rubros
        int rowIdx = 4;
        int siguienteCapitulo = 0;
        List<CapituloHoja> capitulos = p.capitulos();
        for (int filaIdx = 0; filaIdx <= p.filas().size(); filaIdx++) {
            while (siguienteCapitulo < capitulos.size()
                    && capitulos.get(siguienteCapitulo).antesDeFila() <= filaIdx) {
                var cap = capitulos.get(siguienteCapitulo++);
                Row rc = sh.createRow(rowIdx++);
                crearCelda(rc, 0, "Cap. " + neutralizar(cap.item()), capituloEstilo);
                crearCelda(rc, 1, "", capituloEstilo);
                crearCelda(rc, 2, neutralizar(cap.descripcion()), capituloEstilo);
                for (int c = 3; c < columnas; c++) crearCelda(rc, c, "", capituloEstilo);
                crearCeldaNumerica(rc, 6, cap.total(), capituloDinero);
            }
            if (filaIdx == p.filas().size()) break;
            FilaHoja fh = p.filas().get(filaIdx);
            Row r = sh.createRow(rowIdx++);
            crearCelda(r, 0, neutralizar(fh.fila().item()), textoEstilo);
            crearCelda(r, 1, neutralizar(fh.fila().codigo()), textoEstilo);
            crearCelda(r, 2, neutralizar(fh.fila().descripcion()), textoEstilo);
            crearCelda(r, 3, neutralizar(fh.fila().unidad()), textoEstilo);
            crearCeldaNumerica(r, 4, fh.fila().cantidad(), dineroEstilo);
            crearCeldaNumerica(r, 5, fh.fila().precioUnitario(), dineroEstilo);
            crearCeldaNumerica(r, 6, fh.fila().precioTotal(), dineroEstilo);
            crearCeldaNumerica(r, 7, fh.fila().peso(), pctEstilo);
            // Períodos: porcentaje parcial. Celda vacía si la columna no es activa; cero numérico si la
            // columna es activa con valor 0.
            for (int i = 0; i < p.numeroPeriodos(); i++) {
                BigDecimal pct = i < fh.porcentajesPorPeriodo().size()
                        ? fh.porcentajesPorPeriodo().get(i)
                        : null;
                if (pct == null) {
                    crearCelda(r, 8 + i, "", textoEstilo);
                } else {
                    crearCeldaNumerica(r, 8 + i, pct, pctEstilo);
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

        rowIdx = escribirResumen(sh, rowIdx, "% PARCIAL", parcialPct, resumenEstilo, resumenPct);
        rowIdx = escribirResumen(sh, rowIdx, "% ACUMULADO", acumPct, resumenEstilo, resumenPct);
        rowIdx = escribirResumen(sh, rowIdx, "MONTO PARCIAL", parcialMonto, resumenEstilo, resumenDinero);
        escribirResumen(sh, rowIdx, "MONTO ACUMULADO", acumMonto, resumenEstilo, resumenDinero);

        // Auto-anchos razonables.
        for (int i = 0; i < columnas; i++) {
            sh.autoSizeColumn(i);
        }
    }

    private static int escribirResumen(
            Sheet sh, int rowIdx, String etiqueta, BigDecimal[] valores, CellStyle estilo, CellStyle estiloNumero) {
        Row r = sh.createRow(rowIdx);
        crearCelda(r, 0, etiqueta, estilo);
        for (int i = 0; i < valores.length; i++) {
            crearCeldaNumerica(r, 8 + i, valores[i], estiloNumero);
        }
        return rowIdx + 1;
    }

    private static String formato(int decimales) {
        return decimales <= 0 ? "0" : "0." + "0".repeat(decimales);
    }

    private static CellStyle numerico(Workbook wb, CellStyle base, String formato) {
        CellStyle s = wb.createCellStyle();
        s.cloneStyleFrom(base);
        s.setDataFormat(wb.createDataFormat().getFormat(formato));
        return s;
    }

    private static void crearCelda(Row fila, int col, String texto, CellStyle estilo) {
        Cell c = fila.createCell(col);
        c.setCellValue(texto);
        if (estilo != null) {
            c.setCellStyle(estilo);
        }
    }

    private static void crearCeldaNumerica(Row fila, int col, BigDecimal valor, CellStyle estilo) {
        Cell c = fila.createCell(col);
        c.setCellStyle(estilo);
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
