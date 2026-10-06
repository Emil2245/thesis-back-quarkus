package ec.uce.propuestas.documento;

import ec.uce.propuestas.documento.exportacion.ApuDocumentoProyeccion;
import ec.uce.propuestas.documento.exportacion.ApuDocumentoProyeccion.Documento;
import ec.uce.propuestas.documento.exportacion.OpcionesDocumento;
import ec.uce.propuestas.motor.SeccionTipo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STCellType;

/** Pure XLSX serialization of the selected, persisted documentary projection. */
public final class ApuXlsxWriter {
    private ApuXlsxWriter() {}

    public static byte[] renderizar(Documento documento, OpcionesDocumento opciones) {
        if (!"xlsx".equals(opciones.formato())
                || !opciones.opciones().keySet().equals(Set.of("layout"))
                || !Set.of("pestanas", "apilado").contains(opciones.opciones().get("layout"))) {
            throw new IllegalArgumentException("Opciones incompatibles con APU XLSX");
        }
        try (var wb = new XSSFWorkbook();
                var out = new ByteArrayOutputStream()) {
            var estilos = new Estilos(wb, documento);
            boolean apilado = "apilado".equals(opciones.opciones().get("layout"));
            XSSFSheet comun = apilado && !documento.apus().isEmpty() ? wb.createSheet("APUs") : null;
            var nombres = new HashSet<String>();
            int offset = 0;
            for (var apu : documento.apus()) {
                var sheet = apilado ? comun : wb.createSheet(nombre(apu, nombres));
                if (offset > 0 && apilado) sheet.setRowBreak(offset - 1);
                var cursor = new Cursor(sheet, apilado ? offset : 0, estilos, apu);
                bloque(cursor, documento, apu);
                cursor.volcar();
                configurar(wb, sheet, cursor.fila);
                offset = cursor.fila;
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el XLSX de APUs", e);
        }
    }

    private static void bloque(Cursor c, Documento d, ApuDocumentoProyeccion.Analisis a) {
        c.linea("ANÁLISIS DE PRECIOS UNITARIOS", true);
        c.linea(d.proyecto().direccionInstitucional(), false);
        c.linea(d.proyecto().subdireccionInstitucional(), false);
        if (a.nombreProyectoHeader() != null) c.linea("Proyecto: " + a.nombreProyectoHeader(), false);
        c.linea("Código: " + a.etiquetaCodigo(), true);
        c.linea(a.descripcion(), false);
        c.linea("Unidad: " + a.unidad(), false);
        for (var b : a.bloques()) {
            boolean horario = b.tipo() == SeccionTipo.EQUIPO || b.tipo() == SeccionTipo.MANO_OBRA;
            c.seccion(
                    b.etiqueta(),
                    horario
                            ? List.of(
                                    "Descripción", "Cantidad", "Tarifa / Jornal", "Costo hora", "Rendimiento", "Costo")
                            : List.of("Descripción", "Unidad", "Cantidad", "Precio / Tarifa", "", "Costo"));
            for (var f : b.filas()) {
                var v = f.detalle();
                var chunks = ApuTextoPresentacion.fragmentos(f.descripcion());
                var unidades = ApuTextoPresentacion.fragmentos(v.unidad());
                int count = Math.max(chunks.size(), horario ? 0 : unidades.size());
                for (int i = 0; i < count; i++) {
                    var row = c.nueva();
                    c.texto(row, 0, i < chunks.size() ? chunks.get(i) : "", false);
                    if (!horario) c.texto(row, 1, i < unidades.size() ? unidades.get(i) : "", false);
                    if (i != 0) continue;
                    if (horario) {
                        c.numero(row, 1, v.cantidad(), c.estilos.efectivo);
                        c.numero(row, 2, v.precioEfectivo(), c.estilos.dinero);
                        c.numero(row, 3, v.costoHora(), c.estilos.dinero);
                        c.numero(row, 4, v.rendimiento(), c.estilos.efectivo);
                    } else {
                        c.numero(row, 2, v.cantidad(), c.estilos.efectivo);
                        c.numero(row, 3, v.precioEfectivo(), c.estilos.dinero);
                    }
                    c.numero(row, 5, v.costo(), c.estilos.dinero);
                }
            }
            if (b.mostrarSubtotal()) c.importe("Subtotal " + b.etiqueta(), b.subtotal());
            c.cerrarSeccion();
        }
        for (var subtotal : a.pie().subtotales()) c.importe("Subtotal " + subtotal.tipo(), subtotal.valor());
        c.importe("Costo directo", a.pie().costoDirecto());
        var ci = c.nueva();
        c.texto(ci, 0, "Costo indirecto", true);
        c.numero(ci, 4, a.pie().porcentajeIndirecto(), c.estilos.porcentaje);
        c.numero(ci, 5, a.pie().costoIndirecto(), c.estilos.dinero);
        c.importe("Costo total", a.pie().costoTotal());
        c.importe("Valor ofertado", a.pie().valorOfertado());
        c.linea(a.pie().mensaje(), false);
        d.firmantes().stream()
                .sorted(java.util.Comparator.comparingInt(
                                ec.uce.propuestas.documento.exportacion.SnapshotDocumento.Firmante::orden)
                        .thenComparing(f -> f.id().toString()))
                .forEach(f -> c.linea(
                        PresupuestoXlsxWriter.seguro(f.nombre()) + " · "
                                + PresupuestoXlsxWriter.seguro(f.cargo()) + " · "
                                + PresupuestoXlsxWriter.seguro(f.rol()),
                        false));
    }

    private static String nombre(ApuDocumentoProyeccion.Analisis a, Set<String> usados) {
        String base = PresupuestoXlsxWriter.seguro(a.codigo()).replaceAll("[\\[\\]:*?/\\\\\\p{Cc}]", " ");
        base = base.strip().replaceAll("^'+|'+$", "").strip();
        if (base.isEmpty()) base = "APU";
        base = cortar(base, 31).replaceAll("'+$", "");
        String result = base;
        int intento = 0;
        while (result.equalsIgnoreCase("History") || !usados.add(result.toLowerCase(Locale.ROOT))) {
            String suffix = "-" + a.id().toString().replace("-", "").substring(0, 8) + "-" + (++intento);
            result = cortar(base, 31 - suffix.length()) + suffix;
        }
        return result;
    }

    private static String cortar(String s, int max) {
        int end = Math.min(s.length(), max);
        if (end > 0 && Character.isHighSurrogate(s.charAt(end - 1))) end--;
        return s.substring(0, end);
    }

    private static void configurar(XSSFWorkbook wb, XSSFSheet sheet, int filas) {
        int[] widths = {44, 14, 20, 20, 20, 20};
        for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
        sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
        sheet.getPrintSetup().setLandscape(false);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
        sheet.setFitToPage(true);
        // 650 unscaled points leave reserve within usable A4 height; fit-width
        // reduces this deliberately wide table rather than enlarging it.
        sheet.setMargin(org.apache.poi.ss.usermodel.Sheet.TopMargin, 0.5);
        sheet.setMargin(org.apache.poi.ss.usermodel.Sheet.BottomMargin, 0.5);
        sheet.setMargin(org.apache.poi.ss.usermodel.Sheet.LeftMargin, 0.4);
        sheet.setMargin(org.apache.poi.ss.usermodel.Sheet.RightMargin, 0.4);
        wb.setPrintArea(wb.getSheetIndex(sheet), 0, 5, 0, filas - 1);
    }

    private static final class Estilos {
        final CellStyle texto, titulo, dinero, porcentaje, efectivo;

        Estilos(XSSFWorkbook wb, Documento d) {
            texto = wb.createCellStyle();
            texto.setWrapText(true);
            texto.setVerticalAlignment(VerticalAlignment.TOP);
            titulo = wb.createCellStyle();
            titulo.cloneStyleFrom(texto);
            var font = wb.createFont();
            font.setBold(true);
            titulo.setFont(font);
            dinero = numerico(
                    wb,
                    "0"
                            + (d.display().precision() == 0
                                    ? ""
                                    : "." + "0".repeat(d.display().precision())));
            porcentaje = numerico(
                    wb,
                    "0"
                            + (d.display().precisionPorcentaje() == 0
                                    ? ""
                                    : "." + "0".repeat(d.display().precisionPorcentaje()))
                            + "%");
            efectivo = numerico(wb, "0.##############################");
        }

        private CellStyle numerico(XSSFWorkbook wb, String formato) {
            var style = wb.createCellStyle();
            style.cloneStyleFrom(texto);
            style.setDataFormat(wb.createDataFormat().getFormat(formato));
            return style;
        }
    }

    private record Celda(String texto, BigDecimal numero, CellStyle estilo) {}

    private static final class Fila {
        final java.util.Map<Integer, Celda> celdas = new java.util.TreeMap<>();
        float altura = 24;
        boolean merged;
    }

    private static final class Cursor {
        final XSSFSheet sheet;
        final Estilos estilos;
        final List<String> identidad;
        int fila;
        float alturaPagina;
        Fila pendiente;
        List<Fila> encabezados = List.of();
        boolean primeraFila;

        Cursor(XSSFSheet sheet, int fila, Estilos estilos, ApuDocumentoProyeccion.Analisis apu) {
            this.sheet = sheet;
            this.fila = fila;
            this.estilos = estilos;
            this.identidad = List.of(
                    "APU UUID: " + apu.id(),
                    "APU código: " + vista(apu.etiquetaCodigo()),
                    "APU descripción: " + vista(apu.descripcion()));
            cabecera();
        }

        // Buffer one logical row so its final height is known before pagination.
        // Numeric cells travel with that row and are never copied into headers.
        Fila nueva() {
            volcar();
            pendiente = new Fila();
            return pendiente;
        }

        void volcar() {
            if (pendiente == null) return;
            float grupo = pendiente.altura + (primeraFila ? alturaEncabezados() : 0);
            boolean salto = alturaPagina + grupo > 650;
            if (salto) {
                sheet.setRowBreak(fila - 1);
                alturaPagina = 0;
                cabecera();
            }
            if (primeraFila || salto) encabezados.forEach(this::escribir);
            primeraFila = false;
            escribir(pendiente);
            pendiente = null;
        }

        float alturaEncabezados() {
            float total = 0;
            for (var row : encabezados) total += row.altura;
            return total;
        }

        void seccion(String etiqueta, List<String> columnas) {
            volcar();
            var titulo = new Fila();
            titulo.merged = true;
            texto(titulo, 0, etiqueta, true);
            var esquema = new Fila();
            esquema.altura = 30;
            for (int i = 0; i < columnas.size(); i++) texto(esquema, i, columnas.get(i), true);
            encabezados = List.of(titulo, esquema);
            primeraFila = true;
        }

        void cerrarSeccion() {
            volcar();
            // An empty block without subtotal still has its configured heading and
            // schema, kept together; never synthesize a financial detail for it.
            if (primeraFila) {
                if (alturaPagina + alturaEncabezados() > 650) {
                    sheet.setRowBreak(fila - 1);
                    alturaPagina = 0;
                    cabecera();
                }
                encabezados.forEach(this::escribir);
            }
            encabezados = List.of();
            primeraFila = false;
        }

        void cabecera() {
            for (String text : identidad) {
                var row = new Fila();
                row.merged = true;
                row.altura = 30;
                row.celdas.put(0, new Celda(text, null, estilos.titulo));
                escribir(row);
            }
        }

        private static String vista(String text) {
            if (text == null) return "";
            String single = text.replaceAll("\\s+", " ").strip();
            return single.length() <= 90 ? single : cortar(single, 89) + "…";
        }

        private void escribir(Fila source) {
            if (fila >= 1_048_576) throw new IllegalArgumentException("APU excede límite de filas XLSX");
            var row = sheet.createRow(fila++);
            row.setHeightInPoints(source.altura);
            for (var entry : source.celdas.entrySet()) {
                var cell = row.createCell(entry.getKey());
                var value = entry.getValue();
                cell.setCellStyle(value.estilo());
                if (value.numero() == null) cell.setCellValue(value.texto());
                else {
                    cell.getCTCell().setT(STCellType.N);
                    cell.getCTCell().setV(value.numero().toPlainString());
                }
            }
            // Each merge spans only this newly emitted row: no overlap with prior
            // merges or array formulas (the writer never emits formulas).
            if (source.merged)
                sheet.addMergedRegionUnsafe(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, 5));
            alturaPagina += source.altura;
        }

        void texto(Fila row, int col, String value, boolean bold) {
            row.celdas.put(col, new Celda(value, null, bold ? estilos.titulo : estilos.texto));
            int lines = 0;
            for (String line : value.split("\n", -1))
                lines += Math.max(1, (line.codePointCount(0, line.length()) + 29) / 30);
            row.altura = Math.max(row.altura, Math.min(240, lines * 15 + 15));
        }

        void numero(Fila row, int col, BigDecimal value, CellStyle style) {
            if (value != null) row.celdas.put(col, new Celda(null, value, style));
        }

        void linea(String value, boolean bold) {
            if (value == null || value.isBlank()) return;
            for (String chunk : ApuTextoPresentacion.fragmentos(value)) {
                var row = nueva();
                texto(row, 0, chunk, bold);
                row.merged = true;
            }
        }

        void textos(List<String> values, boolean bold) {
            var row = nueva();
            row.altura = 30;
            for (int i = 0; i < values.size(); i++) texto(row, i, values.get(i), bold);
        }

        void importe(String label, BigDecimal value) {
            var row = nueva();
            row.altura = 30;
            texto(row, 0, label, true);
            numero(row, 5, value, estilos.dinero);
        }
    }
}
