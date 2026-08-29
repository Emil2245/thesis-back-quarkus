package ec.uce.propuestas.plantilla.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Plan 04 (P-26) — Mapper bidireccional entre el snapshot JSONB de
 * {@code plantilla_apu} y la lista de filas canónicas con las que se
 * materializa un APU. La forma canónica se documenta en
 * {@code domain/02-data-model.md §12}:
 * <pre>
 * {
 *   "secciones": [
 *     { "tipo": "EQUIPO", "lineas": [ { "esHerramientaMenor": true } ] },
 *     { "tipo": "MANO_OBRA",
 *       "lineas": [ { "insumoCodigo": "MO-001", "cantidad": "0.1", "rendimiento": "0.5" } ] },
 *     { "tipo": "MATERIAL", "lineas": [] },
 *     { "tipo": "TRANSPORTE", "lineas": [] }
 *   ]
 * }
 * </pre>
 *
 * <p><b>Snapshot price-free (Plan 04 §1, N04 §B.4):</b> el writer NUNCA
 * persiste precios efectivos, IDs de insumo ni referencias al APU origen.
 * Sólo conserva código, cantidad, rendimiento, sección, orden y fila HM.
 * Al aplicar la plantilla, los precios efectivos siempre se recalculan
 * desde la base PROYECTO del proyecto destino (con fallback a CENTRAL /
 * PERSONAL del dueño del proyecto). Esta regla elimina el "override
 * histórico" del APU origen: cargar una plantilla nunca arrastra un precio
 * viejo, evita drift y garantiza paridad con APUs creados desde cero.
 *
 * <p><b>Tolerancia hacia atrás (seed V004):</b> el writer nunca emite
 * campos extra; el reader IGNORA silenciosamente las claves heredadas
 * {@code precioOverride}, {@code tarifaJornal}, {@code precioUnitarioTarifa},
 * {@code costo} y demás (orden, descripcion, publicId, etc.). Jackson está
 * configurado globalmente para ignorar unknown fields por defecto, pero
 * incluso si la configuración los rechazara, este reader los consume por
 * nombre propio y los descarta sin mapearlos a ningún campo del snapshot
 * canónico.
 *
 * <p><b>Secciones:</b> el reader no exige las 4 secciones canónicas; si
 * falta alguna se interpreta como sección vacía (la carga posterior crea
 * las 4 secciones canónicas del APU igualmente).
 */
@ApplicationScoped
public class SnapshotApuMapper {

    public record SnapshotLinea(
            String seccionTipo,
            String insumoCodigo,
            BigDecimal cantidad,
            BigDecimal rendimiento) {}

    public record SnapshotFila(
            String seccionTipo,
            boolean esHerramientaMenor,
            String insumoCodigo,
            BigDecimal cantidad,
            BigDecimal rendimiento) {}

    public record SnapshotBloque(String tipo, List<SnapshotFila> lineas) {}

    public record Snapshot(List<SnapshotBloque> bloques) {}

    @Inject
    ObjectMapper objectMapper;

    // =========================================================================
    // WRITER (price-free — el compilador no permite fijar precios aquí)
    // =========================================================================

    /**
     * Construye el JSONB canónico para {@code plantilla_apu.snapshot_secciones}.
     * Nunca persiste precios efectivos, IDs de insumo ni referencias al APU
     * origen: solo los códigos, cantidades, rendimientos, secciones y orden
     * (preservando el bloque M/N/O/P original). La fila HM se serializa
     * como un placeholder con {@code esHerramientaMenor=true}.
     *
     * <p>El compilador previene el uso accidental de campos de precio: las
     * records internas ({@link SnapshotFila}, {@link SnapshotLinea}) ya no
     * los exponen. Toda decisión de precio se delega a la base del proyecto
     * al aplicar la plantilla.
     */
    public String escribir(List<SnapshotBloque> bloques) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ArrayNode secciones = JsonNodeFactory.instance.arrayNode();
        for (SnapshotBloque b : bloques) {
            ObjectNode seccion = JsonNodeFactory.instance.objectNode();
            seccion.put("tipo", b.tipo);
            ArrayNode lineas = JsonNodeFactory.instance.arrayNode();
            for (SnapshotFila fila : b.lineas) {
                ObjectNode n = JsonNodeFactory.instance.objectNode();
                if (fila.esHerramientaMenor) {
                    n.put("esHerramientaMenor", true);
                } else {
                    if (fila.insumoCodigo != null) {
                        n.put("insumoCodigo", fila.insumoCodigo);
                    }
                    if (fila.cantidad != null) {
                        n.put("cantidad", fila.cantidad.toPlainString());
                    }
                    if (fila.rendimiento != null) {
                        n.put("rendimiento", fila.rendimiento.toPlainString());
                    }
                    // NUNCA se persiste ningún campo de precio (precioOverride,
                    // tarifaJornal, precioUnitarioTarifa, costo). Los precios
                    // siempre se resuelven desde la base del proyecto al aplicar
                    // la plantilla (snapshot price-free — Plan 04 §1).
                }
                lineas.add(n);
            }
            seccion.set("lineas", lineas);
            secciones.add(seccion);
        }
        root.set("secciones", secciones);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo serializar snapshot de plantilla APU", e);
        }
    }

    // =========================================================================
    // READER (lenient: ignora campos extra, incluyendo precios heredados)
    // =========================================================================

    /** Lee el JSONB y devuelve los bloques en orden (E, MO, MAT, TR) preservando el orden original. */
    public List<SnapshotBloque> leer(String jsonb) {
        if (jsonb == null || jsonb.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(jsonb);
            JsonNode secciones = root.get("secciones");
            if (secciones == null || !secciones.isArray()) {
                return List.of();
            }
            List<SnapshotBloque> out = new ArrayList<>();
            for (JsonNode sec : secciones) {
                String tipo = textOrNull(sec, "tipo");
                if (tipo == null) {
                    continue; // sección inválida: la saltamos
                }
                List<SnapshotFila> filas = new ArrayList<>();
                JsonNode lineas = sec.get("lineas");
                if (lineas != null && lineas.isArray()) {
                    for (JsonNode l : lineas) {
                        filas.add(leerFila(l));
                    }
                }
                out.add(new SnapshotBloque(tipo, filas));
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("snapshot_secciones corrupto: " + e.getMessage(), e);
        }
    }

    private static SnapshotFila leerFila(JsonNode l) {
        boolean hm = l.path("esHerramientaMenor").asBoolean(false);
        String insumoCodigo = textOrNull(l, "insumoCodigo");
        BigDecimal cantidad = decimalOrNull(l, "cantidad");
        BigDecimal rendimiento = decimalOrNull(l, "rendimiento");
        // Ignoramos silenciosamente los campos de precio heredados del seed V004
        // (precioOverride, tarifaJornal, precioUnitarioTarifa, costo) y los
        // campos auxiliares (orden, descripcion, seccionTipo, tipoInsumo, publicId).
        // El snapshot canónico no transporta precios — son irrelevantes al cargar
        // la plantilla (los precios se recalculan desde la base destino).
        return new SnapshotFila(null, hm, insumoCodigo, cantidad, rendimiento);
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) return null;
        return v.asText();
    }

    private static BigDecimal decimalOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) return null;
        if (v.isNumber()) return new BigDecimal(v.asText());
        if (v.isTextual()) {
            String s = v.asText();
            if (s == null || s.isBlank()) return null;
            return new BigDecimal(s);
        }
        return null;
    }

    /** Parseo a JsonNode opaco para devolverlo en el response. */
    public JsonNode parseJson(String jsonb) {
        if (jsonb == null || jsonb.isBlank()) {
            return JsonNodeFactory.instance.objectNode();
        }
        try {
            return objectMapper.readTree(jsonb);
        } catch (Exception e) {
            throw new RuntimeException("snapshot_secciones corrupto: " + e.getMessage(), e);
        }
    }

    /** Itera todas las filas no-HM en el orden persistido (preserva orden/sección). */
    public List<SnapshotLinea> lineasNoHm(String jsonb) {
        List<SnapshotLinea> out = new ArrayList<>();
        for (SnapshotBloque b : leer(jsonb)) {
            for (SnapshotFila f : b.lineas) {
                if (f.esHerramientaMenor) continue;
                out.add(new SnapshotLinea(b.tipo, f.insumoCodigo, f.cantidad, f.rendimiento));
            }
        }
        return out;
    }

    /** Devuelve el orden de filas (incluyendo HM) en el snapshot — útil para preservar HM. */
    public List<SnapshotFila> lineasEnOrden(String jsonb) {
        List<SnapshotFila> out = new ArrayList<>();
        Iterator<SnapshotBloque> it = leer(jsonb).iterator();
        while (it.hasNext()) {
            out.addAll(it.next().lineas);
        }
        return out;
    }

    /** Tipos de sección presentes en el snapshot (puede omitir algunos, p.ej. sin filas). */
    public List<String> tiposSecciones(String jsonb) {
        List<String> out = new ArrayList<>();
        for (SnapshotBloque b : leer(jsonb)) {
            out.add(b.tipo);
        }
        return out;
    }
}
