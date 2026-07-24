package ec.uce.propuestas.motor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Test helper: loads fixture JSON and maps to motor snapshot types.
 *
 * JSON field observations (from reading apus-sample-apus-cetro-medico-tulcan.json):
 *  - Top-level: array of APU objects with: sheet, codigo, descripcion, unidad, secciones,
 *    costoDirecto, porcentajeIndirecto, costoTotal
 *  - secciones: array of { tipo: "EQUIPO"|"MANO_OBRA"|"MATERIAL"|"TRANSPORTE",
 *                           subtotal, lineas: [...] }
 *  - EQUIPO lineas: { descripcion, cantidad, tarifa (nullable), costoHora, rendimiento (string or number),
 *                     costo, esHerramientaMenor }
 *  - MANO_OBRA lineas: { descripcion, cantidad, jornal, costoHora, rendimiento, costo, esHerramientaMenor }
 *  - MATERIAL lineas: { descripcion, unidad, cantidad, precioUnitario, costo, esHerramientaMenor }
 *  - TRANSPORTE lineas: same shape as MATERIAL
 *  - HM row in EQUIPO: esHerramientaMenor=true, tarifa=null, rendimiento="" (empty string)
 *
 * presupuesto JSON: flat array with { item, depth, kind, codigo, descripcion, unidad,
 *                                      cantidad, precioUnitario, precioTotal }
 *   kind = "capitulo" | "subcapitulo" | "rubro"
 *
 * Note on stub APUs for consolidation tests: when an APU's full definition is not in the
 * sample file, we create a stub APU with a single MATERIAL line using precioUnitario from
 * the presupuesto (the workbook's 2dp-rounded CT), marked as auxiliar so Motor assigns
 * CI=0 and CT=precioUnitario. This lets Motor.consolidar compute the correct rubro totals
 * that match the workbook's presupuesto sum.
 */
final class Fixtures {

    private static final ObjectMapper M = new ObjectMapper();

    static JsonNode loadJson(String name) throws IOException {
        try (var in = Fixtures.class.getResourceAsStream("/motor/fixtures/" + name)) {
            if (in == null) throw new FileNotFoundException(name);
            return M.readTree(in);
        }
    }

    /**
     * Find an APU node by its codigo in a top-level array.
     */
    static JsonNode findByCodigo(JsonNode apusArray, String codigo) {
        for (JsonNode node : apusArray) {
            JsonNode cod = node.get("codigo");
            if (cod != null && !cod.isNull() && codigo.equals(cod.asText())) {
                return node;
            }
        }
        throw new NoSuchElementException("APU not found: " + codigo);
    }

    /**
     * Map a single APU JSON node to an ApuSnapshot.
     * The node must have a "secciones" array with lineas.
     */
    static ApuSnapshot apuFromJson(JsonNode apuNode) {
        String codigo = apuNode.get("codigo").asText();
        boolean esAuxiliar = false;  // sample APUs all have CI applied (porcentajeIndirecto != null)

        List<FilaSnapshot> filas = new ArrayList<>();

        JsonNode secciones = apuNode.get("secciones");
        for (JsonNode seccion : secciones) {
            SeccionTipo tipo = SeccionTipo.valueOf(seccion.get("tipo").asText());
            JsonNode lineas = seccion.get("lineas");
            for (JsonNode linea : lineas) {
                FilaSnapshot fila = lineaToFila(tipo, linea);
                filas.add(fila);
            }
        }

        return new ApuSnapshot(codigo, esAuxiliar, filas);
    }

    /**
     * Create a stub ApuSnapshot whose Motor.calcularApu result has costoTotal = precioUnitario.
     * Used for rubros not in the APU sample file.
     *
     * Strategy: single MATERIAL line with precioInsumo=precioUnitario, cantidad=1, esAuxiliar=true.
     * With esAuxiliar=true and descuento=0: CT = CD_ajustado = CD = 1 × precioUnitario.
     * This matches the presupuesto's 2dp-rounded precioUnitario for every rubro.
     */
    static ApuSnapshot stubApuFromPrecioUnitario(String codigo, BigDecimal precioUnitario) {
        FilaSnapshot mat = new FilaSnapshot(SeccionTipo.MATERIAL, false,
                BigDecimal.ONE, null, precioUnitario, null, null);
        return new ApuSnapshot(codigo, true, List.of(mat));
    }

    /**
     * Build a VersionSnapshot from the presupuesto + APU sample.
     * For rubros whose APU definition is in the sample file: use the full apuFromJson.
     * For rubros NOT in the sample file: use a stub APU (see stubApuFromPrecioUnitario).
     * This ensures Motor.consolidar can compute a totalGeneral that matches the workbook.
     *
     * @param presupuestoFile  presupuesto-*.json filename
     * @param apusFile         apus-sample-*.json filename
     * @param params           ParametrosCalculo to use
     */
    static VersionSnapshot versionFromJson(String presupuestoFile, String apusFile,
                                            ParametrosCalculo params) throws IOException {
        JsonNode presupuestoArray = loadJson(presupuestoFile);
        JsonNode apusArray = loadJson(apusFile);

        // Build APU lookup by codigo (from sample file)
        Map<String, JsonNode> apuByCode = new LinkedHashMap<>();
        for (JsonNode apuNode : apusArray) {
            JsonNode cod = apuNode.get("codigo");
            if (cod != null && !cod.isNull() && !cod.asText().isEmpty()) {
                apuByCode.put(cod.asText(), apuNode);
            }
        }

        // Reconstruct the chapter hierarchy using stub APUs for missing codes
        List<CapituloSnapshot> rootCaps = buildHierarchy(presupuestoArray, apuByCode);

        return new VersionSnapshot(params, rootCaps, null);
    }

    private static FilaSnapshot lineaToFila(SeccionTipo tipo, JsonNode linea) {
        boolean esHM = linea.has("esHerramientaMenor") && linea.get("esHerramientaMenor").asBoolean();

        if (esHM) {
            // HM row: cantidad = percentage integer (e.g. 5), no precioInsumo, no rendimiento
            BigDecimal cantidad = bigDecimalOrNull(linea, "cantidad");
            return new FilaSnapshot(SeccionTipo.EQUIPO, true, cantidad, null, null, null, null);
        }

        BigDecimal cantidad = bigDecimalOrNull(linea, "cantidad");

        switch (tipo) {
            case EQUIPO: {
                // tarifa = precioInsumo, rendimiento = numeric rendimiento
                BigDecimal tarifa = bigDecimalOrNull(linea, "tarifa");
                BigDecimal rendimiento = bigDecimalOrNull(linea, "rendimiento");
                return new FilaSnapshot(SeccionTipo.EQUIPO, false, cantidad, rendimiento,
                        tarifa, null, null);
            }
            case MANO_OBRA: {
                // jornal = precioInsumo
                BigDecimal jornal = bigDecimalOrNull(linea, "jornal");
                BigDecimal rendimiento = bigDecimalOrNull(linea, "rendimiento");
                return new FilaSnapshot(SeccionTipo.MANO_OBRA, false, cantidad, rendimiento,
                        jornal, null, null);
            }
            case MATERIAL: {
                // precioUnitario = precioInsumo, no rendimiento
                BigDecimal precioUnitario = bigDecimalOrNull(linea, "precioUnitario");
                return new FilaSnapshot(SeccionTipo.MATERIAL, false, cantidad, null,
                        precioUnitario, null, null);
            }
            case TRANSPORTE: {
                // precioUnitario = precioInsumo, no rendimiento multiplication
                BigDecimal precioUnitario = bigDecimalOrNull(linea, "precioUnitario");
                return new FilaSnapshot(SeccionTipo.TRANSPORTE, false, cantidad, null,
                        precioUnitario, null, null);
            }
            default:
                throw new IllegalStateException("Unknown tipo: " + tipo);
        }
    }

    /**
     * Reconstruct the chapter/rubro hierarchy from the flat presupuesto array.
     * Uses a stack to track the current chapter at each depth level.
     * For rubros without an APU in the sample, creates a stub APU from precioUnitario.
     */
    private static List<CapituloSnapshot> buildHierarchy(JsonNode presupuestoArray,
                                                          Map<String, JsonNode> apuByCode) {
        List<CapituloSnapshot> roots = new ArrayList<>();
        Deque<CapituloBuilder> stack = new ArrayDeque<>();

        for (JsonNode row : presupuestoArray) {
            String kind = row.get("kind").asText();
            int depth = row.get("depth").asInt();
            String item = row.get("item").asText();
            String descripcion = row.has("descripcion") && !row.get("descripcion").isNull()
                    ? row.get("descripcion").asText() : "";

            if ("capitulo".equals(kind) || "subcapitulo".equals(kind)) {
                // Pop builders deeper than this depth
                while (!stack.isEmpty() && stack.peek().depth >= depth) {
                    CapituloBuilder finished = stack.pop();
                    CapituloSnapshot built = finished.build();
                    if (stack.isEmpty()) {
                        roots.add(built);
                    } else {
                        stack.peek().subcapitulos.add(built);
                    }
                }
                stack.push(new CapituloBuilder(item, descripcion, depth));
            } else if ("rubro".equals(kind)) {
                JsonNode codigoNode = row.get("codigo");
                if (codigoNode == null || codigoNode.isNull()) continue;
                String codigo = codigoNode.asText();

                BigDecimal cantidad = bigDecimalOrNull(row, "cantidad");
                if (cantidad == null) continue;

                BigDecimal precioUnitario = bigDecimalOrNull(row, "precioUnitario");
                if (precioUnitario == null) continue;

                ApuSnapshot apu;
                JsonNode apuNode = apuByCode.get(codigo);
                if (apuNode != null) {
                    apu = apuFromJson(apuNode);
                } else {
                    // Prefer the fixture's precioTotal (full precision) over the 2dp precioUnitario
                    // so the stub APU's CT reproduces the workbook's row total exactly.
                    BigDecimal precioTotal = bigDecimalOrNull(row, "precioTotal");
                    BigDecimal stubCT;
                    if (precioTotal != null && cantidad.compareTo(BigDecimal.ZERO) != 0) {
                        stubCT = precioTotal.divide(cantidad, 6, RoundingMode.HALF_UP);
                    } else {
                        stubCT = precioUnitario;
                    }
                    apu = stubApuFromPrecioUnitario(codigo, stubCT);
                }

                RubroSnapshot rubro = new RubroSnapshot(codigo, cantidad, apu);
                if (!stack.isEmpty()) {
                    stack.peek().rubros.add(rubro);
                }
            }
        }

        // Drain remaining builders
        while (!stack.isEmpty()) {
            CapituloBuilder finished = stack.pop();
            CapituloSnapshot built = finished.build();
            if (stack.isEmpty()) {
                roots.add(built);
            } else {
                stack.peek().subcapitulos.add(built);
            }
        }

        // Reverse roots since we used a LIFO stack
        Collections.reverse(roots);
        return roots;
    }

    /**
     * Get expected CT values from the APUs array (for cross-checking).
     */
    static Map<String, BigDecimal> expectedCTsByCodigo(JsonNode apusArray) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (JsonNode node : apusArray) {
            JsonNode cod = node.get("codigo");
            JsonNode ct = node.get("costoTotal");
            if (cod != null && !cod.isNull() && ct != null && !ct.isNull()) {
                result.put(cod.asText(), new BigDecimal(ct.asText()));
            }
        }
        return result;
    }

    static BigDecimal bigDecimalOrNull(JsonNode node, String field) {
        if (!node.has(field)) return null;
        JsonNode n = node.get(field);
        if (n.isNull()) return null;
        if (n.isTextual()) {
            String text = n.asText().trim();
            if (text.isEmpty()) return null;
            return new BigDecimal(text);
        }
        if (n.isNumber()) {
            return new BigDecimal(n.asText());
        }
        return null;
    }

    /** Mutable builder for constructing a CapituloSnapshot from a flat list. */
    static final class CapituloBuilder {
        final String item;
        final String descripcion;
        final int depth;
        final List<CapituloSnapshot> subcapitulos = new ArrayList<>();
        final List<RubroSnapshot> rubros = new ArrayList<>();

        CapituloBuilder(String item, String descripcion, int depth) {
            this.item = item;
            this.descripcion = descripcion;
            this.depth = depth;
        }

        CapituloSnapshot build() {
            // Reverse subcapitulos because we added them in LIFO order from the stack
            List<CapituloSnapshot> orderedSubs = new ArrayList<>(subcapitulos);
            Collections.reverse(orderedSubs);
            return new CapituloSnapshot(item, descripcion, depth, orderedSubs, rubros);
        }
    }
}
