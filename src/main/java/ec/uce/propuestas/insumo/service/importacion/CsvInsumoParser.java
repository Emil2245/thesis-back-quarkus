package ec.uce.propuestas.insumo.service.importacion;

import ec.uce.propuestas.insumo.entity.TipoInsumo;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parser de CSV de insumos (P-15) — MODO PURO, sin I/O ni framework.
 * Valida fila a fila (RNF-09): columnas requeridas, tipos, unicidad de código
 * dentro del archivo y reglas por tipo de insumo.
 */
public final class CsvInsumoParser {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT
            .builder()
            .setHeader("codigo", "descripcion", "unidad", "precio")
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .build();

    private CsvInsumoParser() {
    }

    /**
     * @param csv   contenido del archivo (UTF-8).
     * @param tipo  tipo de insumo (define reglas de unidad).
     * @throws IllegalArgumentException si el archivo es ilegible o le faltan columnas.
     */
    public static List<FilaInsumo> parse(byte[] csv, TipoInsumo tipo) {
        if (csv == null || csv.length == 0) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        String contenido = new String(csv, StandardCharsets.UTF_8);
        List<FilaInsumo> filas = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        try (CSVParser parser = FORMAT.parse(new StringReader(contenido))) {
            for (CSVRecord r : parser) {
                String codigo = r.get("codigo");
                String descripcion = r.get("descripcion");
                String unidad = r.get("unidad");
                String precio = r.get("precio");
                filas.add(validarFila(r.getRecordNumber(), codigo, descripcion, unidad, precio, tipo, vistos));
            }
        } catch (IOException | IllegalArgumentException e) {
            if (e instanceof IllegalArgumentException iae && esErrorDeColumnas(iae)) {
                throw iae;
            }
            throw new IllegalArgumentException("Archivo ilegible o columnas incorrectas: " + e.getMessage());
        }
        if (filas.isEmpty()) {
            throw new IllegalArgumentException("El archivo no contiene filas de datos");
        }
        return filas;
    }

    private static boolean esErrorDeColumnas(IllegalArgumentException iae) {
        String m = iae.getMessage();
        return m != null && (m.contains("no column") || m.contains("header") || m.contains("Column"));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static FilaInsumo validarFila(long num, String codigo, String descripcion, String unidad,
                                          String precio, TipoInsumo tipo, Set<String> vistos) {
        if (isBlank(codigo)) {
            return FilaInsumo.error("codigo", "Código requerido");
        }
        if (isBlank(descripcion)) {
            return FilaInsumo.error("descripcion", "Descripción requerida");
        }
        if (!vistos.add(codigo)) {
            return FilaInsumo.error("codigo", "Código duplicado dentro del archivo");
        }
        BigDecimal precioDec;
        try {
            precioDec = new BigDecimal(precio.trim());
        } catch (Exception e) {
            return FilaInsumo.error("precio", "Precio no es un número válido");
        }
        if (precioDec.compareTo(BigDecimal.ZERO) <= 0) {
            return FilaInsumo.error("precio", "Precio debe ser mayor a 0");
        }
        if (TipoInsumo.esUnidadFijaH(tipo) && !"h".equalsIgnoreCase(unidad)) {
            return FilaInsumo.error("unidad", "Para " + tipo + " la unidad debe ser 'h'");
        }
        return new FilaInsumo(codigo, descripcion, unidad, precioDec, null, null);
    }
}