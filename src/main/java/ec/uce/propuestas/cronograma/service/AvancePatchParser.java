package ec.uce.propuestas.cronograma.service;

import com.fasterxml.jackson.databind.JsonNode;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.cronograma.dto.ActividadProgramarRequest;
import ec.uce.propuestas.cronograma.dto.ActividadProgramarRequest.Distribuir;
import ec.uce.propuestas.cronograma.dto.ActividadProgramarRequest.Mover;
import ec.uce.propuestas.cronograma.dto.ActividadProgramarRequest.Redimensionar;
import ec.uce.propuestas.cronograma.dto.ActividadProgramarRequest.Reemplazar;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plan 029 (P-34) — costura de validación de los bodies
 * {@code PATCH /cronogramas/{id}/actividades/{aid}}. La forma legal por operación
 * está congelada por Plan 026 y este parser convierte {@link JsonNode} en el
 * {@link ActividadProgramarRequest} discriminado sin reemplazar mapas a través
 * de {@code merge} implícito ni relajar restricciones canónicas.
 *
 * <p>Reglas aplicadas en TODA operación antes de tocar la base:
 * <ul>
 *   <li>Body objeto JSON estricto: {@code operacion} obligatoria y exclusivamente
 *       una de las cuatro etiquetas del canon.</li>
 *   <li>Propiedades desconocidas para la operación se rechazan con 400
 *       {@code validacion}; el cliente no puede enviar {@code rubroId},
 *       {@code cronogramaId}, identidades ni campos extra.</li>
 *   <li>Enteros de borde/posición son obligatorios y {@code isIntegralNumber()};
 *       los rangos deben satisfacer {@code 1 <= x <= numeroPeriodos} cuando
 *       aplique.</li>
 * </ul>
 *
 * <p>Validaciones específicas del reemplazo de mapa (Plan 026 §3, §6):
 * <ul>
 *   <li>Las claves son string de enteros 1-based; claves no enteras, fuera de
 *       rango, duplicadas tras normalización (p. ej. {@code "01"} y {@code "1"})
 *       o nulas se rechazan con 400 sin mutación parcial.</li>
 *   <li>Los valores son decimales no negativos a escala máxima 4; valores
 *       {@code null}, no decimales, negativos, o con más de 4 dígitos fraccionarios
 *       (representados como string) devuelven 400.</li>
 *   <li>El mapa vacío ({@code {}}) se acepta como borrador válido.</li>
 *   <li>Una clave presente con valor {@code "0.0000"} es periód activo y participa
 *       en segmentos, mover y redimensionar.</li>
 * </ul>
 *
 * <p>Los algoritmos específicos del cliente (suma exacta, residual, segmento
 * actual) viven en {@link AvancePatchCalculos}; este parser produce
 * únicamente DTOs validados en la frontera.
 */
public final class AvancePatchParser {

    /** Escala canónica de los avances: 4 dígitos fraccionarios como máximo. */
    public static final int SCALE_AVANCE = 4;

    /** Propiedades admitidas por cada operación — el resto se rechaza con 400. */
    private static final Set<String> CAMPOS_REEMPLAZAR = Set.of("operacion", "avancePorPeriodo");

    private static final Set<String> CAMPOS_DISTRIBUIR = Set.of("operacion", "periodos");
    private static final Set<String> CAMPOS_MOVER = Set.of("operacion", "inicio", "fin", "delta");
    private static final Set<String> CAMPOS_REDIMENSIONAR =
            Set.of("operacion", "inicio", "fin", "nuevoInicio", "nuevoFin");

    private AvancePatchParser() {}

    /**
     * Parsea un body JSON y devuelve el {@link ActividadProgramarRequest}
     * discriminado y validado. Lanza {@link ProblemaException} (400
     * {@code validacion}) en cualquier desviación.
     *
     * @param body                 body crudo (acepta {@code null} → 400).
     * @param numeroPeriodosVigente número de períodos del cronograma, usado para
     *                              validar que claves/bordes caigan dentro del
     *                              rango vigente.
     */
    public static ActividadProgramarRequest parsear(JsonNode body, int numeroPeriodosVigente) {
        if (body == null || !body.isObject()) {
            throw ProblemaException.validacion("El cuerpo de la petición debe ser un objeto JSON");
        }
        String operacion = operacion(body);
        return switch (operacion) {
            case ActividadProgramarRequest.OP_REEMPLAZAR -> parsearReemplazar(body, numeroPeriodosVigente);
            case ActividadProgramarRequest.OP_DISTRIBUIR -> parsearDistribuir(body, numeroPeriodosVigente);
            case ActividadProgramarRequest.OP_MOVER -> parsearMover(body, numeroPeriodosVigente);
            case ActividadProgramarRequest.OP_REDIMENSIONAR -> parsearRedimensionar(body, numeroPeriodosVigente);
            default ->
                throw ProblemaException.validacion("operacion debe ser una de: "
                        + ActividadProgramarRequest.OP_REEMPLAZAR + ", "
                        + ActividadProgramarRequest.OP_DISTRIBUIR + ", "
                        + ActividadProgramarRequest.OP_MOVER + ", "
                        + ActividadProgramarRequest.OP_REDIMENSIONAR);
        };
    }

    // ──────────────────────────────────────────────────────────────────────
    // operaciones
    // ──────────────────────────────────────────────────────────────────────

    private static Reemplazar parsearReemplazar(JsonNode body, int numeroPeriodosVigente) {
        exigirCampos(body, CAMPOS_REEMPLAZAR);
        JsonNode mapa = body.get("avancePorPeriodo");
        if (mapa == null || mapa.isNull() || !mapa.isObject()) {
            throw ProblemaException.validacion("avancePorPeriodo debe ser un objeto JSON");
        }
        Map<String, String> validado = validarYNormalizarMapa(mapa, numeroPeriodosVigente);
        return new Reemplazar(ActividadProgramarRequest.OP_REEMPLAZAR, validado);
    }

    private static Distribuir parsearDistribuir(JsonNode body, int numeroPeriodosVigente) {
        exigirCampos(body, CAMPOS_DISTRIBUIR);
        JsonNode periodos = body.get("periodos");
        if (periodos == null || !periodos.isArray()) {
            throw ProblemaException.validacion("periodos es obligatorio y debe ser un arreglo");
        }
        if (periodos.size() == 0) {
            throw ProblemaException.validacion("periodos no puede ser vacío");
        }
        if (periodos.size() > numeroPeriodosVigente) {
            throw ProblemaException.validacion("periodos excede el rango vigente 1.." + numeroPeriodosVigente);
        }
        List<Integer> normales = new ArrayList<>(periodos.size());
        for (JsonNode nodo : periodos) {
            if (nodo == null || !nodo.isIntegralNumber() || !nodo.canConvertToInt()) {
                throw ProblemaException.validacion("Cada período debe ser un entero positivo");
            }
            int p = nodo.asInt();
            if (p < 1 || p > numeroPeriodosVigente) {
                throw ProblemaException.validacion("Período fuera del rango 1.." + numeroPeriodosVigente);
            }
            if (normales.contains(p)) {
                throw ProblemaException.validacion("Períodos duplicados: " + p);
            }
            normales.add(p);
        }
        normales.sort(Integer::compareTo);
        return new Distribuir(ActividadProgramarRequest.OP_DISTRIBUIR, List.copyOf(normales));
    }

    private static Mover parsearMover(JsonNode body, int numeroPeriodosVigente) {
        exigirCampos(body, CAMPOS_MOVER);
        int inicio = borde(body, "inicio", 1, numeroPeriodosVigente);
        int fin = borde(body, "fin", 1, numeroPeriodosVigente);
        if (fin < inicio) {
            throw ProblemaException.validacion("fin debe ser >= inicio");
        }
        JsonNode deltaNodo = body.get("delta");
        if (deltaNodo == null || !deltaNodo.isIntegralNumber() || !deltaNodo.canConvertToInt()) {
            throw ProblemaException.validacion("delta es obligatorio y debe ser un entero");
        }
        int delta = deltaNodo.asInt();
        if (delta == 0) {
            throw ProblemaException.validacion("delta no puede ser cero");
        }
        int nuevoInicio = inicio + delta;
        int nuevoFin = fin + delta;
        if (nuevoInicio < 1 || nuevoFin > numeroPeriodosVigente) {
            throw ProblemaException.validacion("El desplazamiento sale del rango 1.." + numeroPeriodosVigente);
        }
        return new Mover(ActividadProgramarRequest.OP_MOVER, inicio, fin, delta);
    }

    private static Redimensionar parsearRedimensionar(JsonNode body, int numeroPeriodosVigente) {
        exigirCampos(body, CAMPOS_REDIMENSIONAR);
        int inicio = borde(body, "inicio", 1, numeroPeriodosVigente);
        int fin = borde(body, "fin", 1, numeroPeriodosVigente);
        if (fin < inicio) {
            throw ProblemaException.validacion("fin debe ser >= inicio");
        }
        int nuevoInicio = borde(body, "nuevoInicio", 1, numeroPeriodosVigente);
        int nuevoFin = borde(body, "nuevoFin", 1, numeroPeriodosVigente);
        if (nuevoFin < nuevoInicio) {
            throw ProblemaException.validacion("nuevoFin debe ser >= nuevoInicio");
        }
        return new Redimensionar(ActividadProgramarRequest.OP_REDIMENSIONAR, inicio, fin, nuevoInicio, nuevoFin);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private static String operacion(JsonNode body) {
        JsonNode nodo = body.get("operacion");
        if (nodo == null || nodo.isNull()) {
            throw ProblemaException.validacion("operacion es obligatorio");
        }
        if (!nodo.isTextual()) {
            throw ProblemaException.validacion("operacion debe ser un string");
        }
        String valor = nodo.asText();
        if (!ActividadProgramarRequest.OP_REEMPLAZAR.equals(valor)
                && !ActividadProgramarRequest.OP_DISTRIBUIR.equals(valor)
                && !ActividadProgramarRequest.OP_MOVER.equals(valor)
                && !ActividadProgramarRequest.OP_REDIMENSIONAR.equals(valor)) {
            throw ProblemaException.validacion("operacion debe ser una de: "
                    + ActividadProgramarRequest.OP_REEMPLAZAR + ", "
                    + ActividadProgramarRequest.OP_DISTRIBUIR + ", "
                    + ActividadProgramarRequest.OP_MOVER + ", "
                    + ActividadProgramarRequest.OP_REDIMENSIONAR);
        }
        return valor;
    }

    private static void exigirCampos(JsonNode body, Set<String> permitidos) {
        var nombres = body.fieldNames();
        while (nombres.hasNext()) {
            String nombre = nombres.next();
            if (!permitidos.contains(nombre)) {
                throw ProblemaException.validacion("Propiedad no admitida para esta operación: " + nombre);
            }
        }
    }

    private static int borde(JsonNode body, String nombre, int min, int max) {
        JsonNode nodo = body.get(nombre);
        if (nodo == null || !nodo.isIntegralNumber() || !nodo.canConvertToInt()) {
            throw ProblemaException.validacion(nombre + " debe ser un entero");
        }
        int valor = nodo.asInt();
        if (valor < min || valor > max) {
            throw ProblemaException.validacion(nombre + " fuera del rango " + min + ".." + max);
        }
        return valor;
    }

    /**
     * Normaliza el mapa validando cada clave y cada valor. Devuelve un mapa
     * cuyos valores están cuantizados a escala 4 {@code HALF_UP} en string
     * canónico (p. ej. {@code "0.0000"}). Cualquier violación produce 400.
     */
    public static Map<String, String> validarYNormalizarMapa(JsonNode mapa, int numeroPeriodosVigente) {
        Map<String, String> resultado = new LinkedHashMap<>();
        List<Integer> clavesNormalizadas = new ArrayList<>();
        var nombres = mapa.fieldNames();
        while (nombres.hasNext()) {
            String clave = nombres.next();
            Integer normalizada = claveNormalizada(clave, numeroPeriodosVigente, clavesNormalizadas);
            JsonNode valor = mapa.get(clave);
            String texto = valorComoDecimal(valor);
            BigDecimal cuantizado = new BigDecimal(texto).setScale(SCALE_AVANCE, RoundingMode.HALF_UP);
            if (cuantizado.signum() < 0) {
                throw ProblemaException.validacion("Los avances no pueden ser negativos; clave=" + clave);
            }
            if (cuantizado.compareTo(new BigDecimal("1000000")) >= 0) {
                throw ProblemaException.validacion("Avance fuera de rango razonable; clave=" + clave);
            }
            resultado.put(normalizada.toString(), cuantizado.toPlainString());
        }
        return Map.copyOf(resultado);
    }

    /**
     * Valida la forma de la clave (entero 1..{@code numeroPeriodosVigente}) y
     * la unicidad frente a las claves ya normalizadas (descarta duplicados
     * semánticos como {@code "01"} y {@code "1"}).
     */
    static Integer claveNormalizada(String clave, int numeroPeriodosVigente, List<Integer> previas) {
        if (clave == null) {
            throw ProblemaException.validacion("Clave de período ausente");
        }
        // Una clave con espacios o caracteres no numéricos cae como inválida.
        for (int i = 0; i < clave.length(); i++) {
            char c = clave.charAt(i);
            if (c < '0' || c > '9') {
                throw ProblemaException.validacion("Clave de período debe ser un entero positivo: " + clave);
            }
        }
        if (clave.isEmpty()) {
            throw ProblemaException.validacion("Clave de período vacía");
        }
        int valor;
        try {
            valor = Integer.parseInt(clave);
        } catch (NumberFormatException nfe) {
            throw ProblemaException.validacion("Clave de período fuera de rango: " + clave);
        }
        if (valor < 1 || valor > numeroPeriodosVigente) {
            throw ProblemaException.validacion(
                    "Clave de período fuera del rango 1.." + numeroPeriodosVigente + ": " + clave);
        }
        if (previas.contains(valor)) {
            throw ProblemaException.validacion("Período duplicado tras normalizar: " + clave);
        }
        previas.add(valor);
        return valor;
    }

    /**
     * Convierte un nodo JSON textual que representa un valor decimal a string normalizado.
     * Rechaza números JSON, boolean/null/objeto/arreglo. El valor debe ser un decimal
     * finito (no NaN/Infinity).
     */
    static String valorComoDecimal(JsonNode nodo) {
        if (nodo == null || nodo.isNull()) {
            throw ProblemaException.validacion("Valor de avance ausente");
        }
        if (nodo.isTextual()) {
            String texto = nodo.asText();
            try {
                BigDecimal v = new BigDecimal(texto);
                if (v.scale() > SCALE_AVANCE) {
                    throw ProblemaException.validacion("Avance con escala mayor que " + SCALE_AVANCE + ": " + texto);
                }
                return v.toPlainString();
            } catch (NumberFormatException nfe) {
                throw ProblemaException.validacion("Valor de avance no es decimal válido: " + texto);
            }
        }
        throw ProblemaException.validacion("Valor de avance debe ser un decimal string");
    }
}
