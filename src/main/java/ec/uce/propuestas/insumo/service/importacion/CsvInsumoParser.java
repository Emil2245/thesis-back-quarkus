package ec.uce.propuestas.insumo.service.importacion;

import ec.uce.propuestas.insumo.entity.TipoInsumo;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Parser de CSV de insumos (P-15) — MODO PURO, sin I/O ni framework.
 * La cabecera se lee por nombre y en cualquier orden. Requeridas: codigo,
 * descripcion, precio. Opcionales: tipo (MATERIAL por defecto) y unidad
 * (requerida salvo MANO_OBRA/EQUIPO, que son siempre 'h').
 * Valida fila a fila (RNF-09).
 */
public final class CsvInsumoParser {

    private static final CSVFormat FORMAT =
            CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(true).setTrim(true).build();

    private static final int UNIDAD_MAX = 10;

    private CsvInsumoParser() {}

    /**
     * @param csv contenido del archivo (UTF-8).
     * @throws IllegalArgumentException si el archivo es ilegible o le faltan columnas.
     */
    public static List<FilaInsumo> parse(byte[] csv) {
        if (csv == null || csv.length == 0) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        String contenido = new String(csv, StandardCharsets.UTF_8);
        List<FilaInsumo> filas = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        try (CSVParser parser = FORMAT.parse(new StringReader(contenido))) {
            Map<String, Integer> cols = null;
            for (CSVRecord r : parser) {
                if (cols == null) {
                    cols = leerCabecera(r);
                    continue;
                }
                filas.add(validarFila(
                        celda(r, cols, "codigo"),
                        celda(r, cols, "tipo"),
                        celda(r, cols, "descripcion"),
                        celda(r, cols, "unidad"),
                        celda(r, cols, "precio"),
                        vistos));
            }
        } catch (IOException | IllegalStateException e) {
            throw new IllegalArgumentException("Archivo ilegible o columnas incorrectas: " + e.getMessage());
        }
        if (filas.isEmpty()) {
            throw new IllegalArgumentException("El archivo no contiene filas de datos");
        }
        return filas;
    }

    private static Map<String, Integer> leerCabecera(CSVRecord r) {
        Map<String, Integer> cols = new HashMap<>();
        for (int i = 0; i < r.size(); i++) {
            String nombre = r.get(i).replace("﻿", "").trim().toLowerCase(Locale.ROOT);
            if (nombre.equals("preciounitario") || nombre.equals("precio_unitario")) {
                nombre = "precio";
            }
            cols.putIfAbsent(nombre, i);
        }
        for (String req : List.of("codigo", "descripcion", "precio")) {
            if (!cols.containsKey(req)) {
                throw new IllegalArgumentException("Archivo ilegible o columnas incorrectas: falta la columna '" + req
                        + "' (columnas: codigo, descripcion, precio, tipo, unidad)");
            }
        }
        return cols;
    }

    private static String celda(CSVRecord r, Map<String, Integer> cols, String nombre) {
        Integer i = cols.get(nombre);
        return i == null || i >= r.size() ? null : r.get(i);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Acepta nombre del enum y abreviaturas (MAT, MO, EQ, TR, "MANO DE OBRA"). Null si no se reconoce. */
    static TipoInsumo parseTipo(String raw) {
        String t = raw.trim().toUpperCase(Locale.ROOT).replace('_', ' ');
        return switch (t) {
            case "MATERIAL", "MAT" -> TipoInsumo.MATERIAL;
            case "MANO OBRA", "MANO DE OBRA", "MO" -> TipoInsumo.MANO_OBRA;
            case "EQUIPO", "EQ" -> TipoInsumo.EQUIPO;
            case "TRANSPORTE", "TR" -> TipoInsumo.TRANSPORTE;
            default -> null;
        };
    }

    private static FilaInsumo validarFila(
            String codigo, String tipoRaw, String descripcion, String unidad, String precio, Set<String> vistos) {
        if (isBlank(codigo)) {
            return FilaInsumo.error("codigo", "Código requerido");
        }
        if (isBlank(descripcion)) {
            return FilaInsumo.error("descripcion", "Descripción requerida");
        }
        TipoInsumo tipo = TipoInsumo.MATERIAL;
        if (!isBlank(tipoRaw)) {
            tipo = parseTipo(tipoRaw);
            if (tipo == null) {
                return FilaInsumo.error("tipo", "Tipo no reconocido: use MATERIAL, MANO_OBRA, EQUIPO o TRANSPORTE");
            }
        }
        if (!vistos.add(codigo)) {
            return FilaInsumo.error("codigo", "Código duplicado dentro del archivo");
        }
        BigDecimal precioDec;
        try {
            precioDec = new BigDecimal(precio == null ? "" : precio.trim());
        } catch (Exception e) {
            return FilaInsumo.error("precio", "Precio no es un número válido");
        }
        if (precioDec.compareTo(BigDecimal.ZERO) <= 0) {
            return FilaInsumo.error("precio", "Precio debe ser mayor a 0");
        }
        String unidadFinal;
        if (TipoInsumo.esUnidadFijaH(tipo)) {
            if (!isBlank(unidad) && !"h".equalsIgnoreCase(unidad)) {
                return FilaInsumo.error("unidad", "Para " + tipo + " la unidad debe ser 'h'");
            }
            unidadFinal = "h";
        } else {
            if (isBlank(unidad)) {
                return FilaInsumo.error("unidad", "Unidad requerida");
            }
            if (unidad.length() > UNIDAD_MAX) {
                return FilaInsumo.error("unidad", "Unidad excede " + UNIDAD_MAX + " caracteres");
            }
            unidadFinal = unidad;
        }
        return new FilaInsumo(codigo, tipo, descripcion, unidadFinal, precioDec, null, null);
    }
}
