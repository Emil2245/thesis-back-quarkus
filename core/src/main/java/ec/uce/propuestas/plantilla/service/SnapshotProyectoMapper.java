package ec.uce.propuestas.plantilla.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Plan 06 (P-46, N04 §A8) — Mapper bidireccional entre el snapshot JSONB de
 * {@code plantilla_proyecto} y el agregado estructural reconstruible. La forma
 * canónica:
 * <pre>
 * {
 *   "cabecera": { "codigo": "P-001", "descripcion": "...",
 *                  "anio": 2026, "fechaInicio": "2026-01-15",
 *                  "plazoEjecucion": 4, "plazoUnidad": "MES",
 *                  "direccionInstitucional": "UCE",
 *                  "subdireccionInstitucional": "...",
 *                  "tituloEt1": "ESPECIFICACIONES TÉCNICAS",
 *                  "tituloEt2": null },
 *   "parametros": { "porcentajeHerramientaMenor": "0.0500",
 *                   "porcentajeIndirecto": "0.1800", "iva": "0.1500", "moneda": "USD",
 *                   "mostrarSeccionesVacias": true, "sufijosSeccionActivos": true,
 *                   "mostrarSubtotalesSeccion": true, "mostrarSubtotalesPie": false,
 *                   "mostrarNombreProyectoHeader": false, "enumerarApus": false,
 *                   "mensajeFooter": "Este precio no incluye IVA",
 *                   "modoCodigoRubro": "AUTOGENERADO" },
 *   "titulos": { "tituloEt1": null, "tituloEt2": null },
 *   "capitulos": [
 *     { "item": "1", "descripcion": "...", "orden": 1, "parentItem": null,
 *       "hijos": [
 *         { "item": "1.1", "descripcion": "...", "orden": 1, "parentItem": "1",
 *           "rubros": [
 *             { "item": "1.1.1", "codigo": "RP-001", "descripcion": "...", "unidad": "m2",
 *               "apu": { "codigo": "RP-001", "descripcion": "...", "unidad": "m2",
 *                        "filas": [
 *                          { "seccionTipo": "EQUIPO", "esHerramientaMenor": true },
 *                          { "seccionTipo": "MANO_OBRA", "insumoCodigo": "MO-001",
 *                            "cantidad": "0.100000", "rendimiento": "0.100000" }
 *                        ] } }
 *           ] }
 *       ] }
 *   ]
 * }
 * </pre>
 *
 * <p><b>Cabecera reutilizable (P-46, N04 §A8):</b> el bloque
 * {@code cabecera} arrastra los campos del proyecto que son seguros de
 * replicar al aplicar — codigo, descripcion, anio, fechaInicio, plazo y
 * unidad, direccionInstitucional, subdireccionInstitucional y los títulos ET.
 * Se excluyen explícitamente: IDs de base/proyecto/rubro/APU/cronograma/
 * firmantes, owner ({@code usuarioId}), estado (siempre BORRADOR al
 * aplicar), logo (binario), nombre del proyecto (autoritativo del request
 * {@code POST /proyectos/desde-plantilla/{id}}), lineage
 * ({@code plantilla_proyecto_origen_id}), timestamps. El bloque {@code titulos}
 * se conserva para compatibilidad hacia atrás con snapshots previos; los
 * campos {@code tituloEt1}/{@code tituloEt2} se leen también desde
 * {@code cabecera} si están presentes (cabecera es la fuente canónica).
 *
 * <p><b>Snapshot price-free y estructural (Plan 06 §1, N04 §A8, P-46):</b> el
 * writer NUNCA persiste precios efectivos, IDs de base / proyecto / rubro /
 * cronograma / firmantes, cantidades de obra (rubros), ni log/history. Sólo
 * los campos estructurales y los coeficientes de APU estructural
 * ({@code cantidad} / {@code rendimiento} de filas APU). Cargar la plantilla
 * nunca arrastra un precio viejo ni un ID origen. La decisión 1:1 de D-09
 * (un APU por rubro) hace innecesario deduplicar APUs entre rubros.
 *
 * <p><b>Tolerancia hacia atrás (seed V004):</b> el writer nunca emite
 * campos extra; el reader IGNORA silenciosamente las claves heredadas (precios,
 * IDs, log/history). Jackson está configurado globalmente para ignorar unknown
 * fields por defecto, pero incluso si la configuración los rechazara, este
 * reader los consume por nombre propio y los descarta sin mapearlos a ningún
 * campo del snapshot canónico.
 *
 * <p><b>Secciones:</b> el reader tolera cualquier subconjunto de las claves
 * canónicas. Si falta {@code cabecera}, el servicio usa los defaults
 * "Pendiente de editar" (mínima cabecera editable, anio actual, plazo 4 MES).
 * Si falta {@code parametros}, el servicio reutiliza los defaults del
 * singleton {@code ParametrosSistema}. Si falta {@code titulos}, queda en
 * {@code null} y el export usa los defaults.
 */
@ApplicationScoped
public class SnapshotProyectoMapper {

    /**
     * Cabecera reutilizable (P-46) — campos del proyecto seguros de replicar al
     * aplicar. Excluye IDs, owner, estado, logo, {@code nombreProyecto}
     * (autoritativo del request), lineage y timestamps. Si falta
     * {@code cabecera}, el servicio usa defaults editables (año actual, 4 MES,
     * "Pendiente de editar") como fallback backward-compatible para los
     * snapshots V004 mínimos.
     */
    public record SnapshotCabecera(
            String codigo,
            String descripcion,
            Short anio,
            LocalDate fechaInicio,
            Short plazoEjecucion,
            String plazoUnidad,
            String direccionInstitucional,
            String subdireccionInstitucional,
            String tituloEt1,
            String tituloEt2) {}

    /** Sub-bloque {@code parametros} — defaults del sistema si faltan. */
    public record SnapshotParametros(
            BigDecimal porcentajeHerramientaMenor,
            BigDecimal porcentajeIndirecto,
            BigDecimal iva,
            String moneda,
            Boolean mostrarSeccionesVacias,
            Boolean sufijosSeccionActivos,
            Boolean mostrarSubtotalesSeccion,
            Boolean mostrarSubtotalesPie,
            Boolean mostrarNombreProyectoHeader,
            Boolean enumerarApus,
            String mensajeFooter,
            String modoCodigoRubro) {}

    /**
     * Sub-bloque {@code titulos} — defaults en N04 §ESP. Conservado por
     * compatibilidad hacia atrás con snapshots previos; los campos
     * {@code tituloEt1}/{@code tituloEt2} ya forman parte de la cabecera
     * canónica. Si están duplicados, cabecera gana.
     */
    public record SnapshotTitulos(String tituloEt1, String tituloEt2) {}

    /**
     * Fila APU estructural (cantidad/rendimiento de la fila, no cantidad de
     * obra del rubro). Mismas reglas que {@code SnapshotApuMapper.SnapshotFila}
     * — writer price-free, reader tolerante.
     */
    public record SnapshotFilaApu(
            String seccionTipo,
            boolean esHerramientaMenor,
            String insumoCodigo,
            BigDecimal cantidad,
            BigDecimal rendimiento) {}

    /** APU estructural embebido en cada rubro (D-09 1:1). */
    public record SnapshotApuEstructural(
            String codigo, String descripcion, String unidad, List<SnapshotFilaApu> filas) {}

    /**
     * Sub-bloque estructural de un rubro. Una rubro nunca arrastra
     * {@code cantidad} (cantidad de obra = dato operativo, no estructural) ni
     * precios efectivos. Sólo se persisten los códigos, descripción, unidad, y
     * el APU estructural asociado.
     */
    public record SnapshotRubro(
            String item, String codigo, String descripcion, String unidad, SnapshotApuEstructural apu) {}

    /** Capítulo recursivo (hijos + rubros con sus APUs estructurales). */
    public record SnapshotCapitulo(
            String item,
            String descripcion,
            int orden,
            String parentItem,
            List<SnapshotCapitulo> hijos,
            List<SnapshotRubro> rubros) {}

    /** Snapshot completo. */
    public record Snapshot(
            SnapshotCabecera cabecera,
            SnapshotParametros parametros,
            SnapshotTitulos titulos,
            List<SnapshotCapitulo> capitulos) {}

    @Inject
    ObjectMapper objectMapper;

    // =========================================================================
    // WRITER (price-free y estructural — el compilador no permite fijar precios
    // ni IDs ni cantidades de obra aquí)
    // =========================================================================

    /**
     * Construye el JSONB canónico para {@code plantilla_proyecto.snapshot_estructura}.
     * NUNCA persiste precios efectivos, IDs de insumo / proyecto / rubro /
     * cronograma / firmantes, ni {@code rubro.cantidad} (cantidades de obra).
     * Sólo los campos estructurales y los coeficientes de APU estructural
     * ({@code cantidad} / {@code rendimiento} de filas APU). Las decisiones de
     * precio y cantidad se delegan al aplicar la plantilla — base PROYECTO del
     * nuevo proyecto + fallback P-26 (PROYECTO → CENTRAL → PERSONAL →
     * pendiente con override 0).
     */
    public String escribir(Snapshot snap) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        if (snap.cabecera() != null) {
            root.set("cabecera", writeCabecera(snap.cabecera()));
        }
        if (snap.parametros() != null) {
            root.set("parametros", writeParametros(snap.parametros()));
        }
        if (snap.titulos() != null) {
            root.set("titulos", writeTitulos(snap.titulos()));
        }
        if (snap.capitulos() != null) {
            ArrayNode caps = JsonNodeFactory.instance.arrayNode();
            for (SnapshotCapitulo c : snap.capitulos()) {
                caps.add(writeCapitulo(c));
            }
            root.set("capitulos", caps);
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo serializar snapshot de plantilla de proyecto", e);
        }
    }

    private ObjectNode writeCabecera(SnapshotCabecera c) {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        if (c.codigo() != null) n.put("codigo", c.codigo());
        if (c.descripcion() != null) n.put("descripcion", c.descripcion());
        if (c.anio() != null) n.put("anio", c.anio().intValue());
        if (c.fechaInicio() != null) n.put("fechaInicio", c.fechaInicio().toString());
        if (c.plazoEjecucion() != null)
            n.put("plazoEjecucion", c.plazoEjecucion().intValue());
        if (c.plazoUnidad() != null) n.put("plazoUnidad", c.plazoUnidad());
        if (c.direccionInstitucional() != null) n.put("direccionInstitucional", c.direccionInstitucional());
        if (c.subdireccionInstitucional() != null) n.put("subdireccionInstitucional", c.subdireccionInstitucional());
        if (c.tituloEt1() != null) n.put("tituloEt1", c.tituloEt1());
        if (c.tituloEt2() != null) n.put("tituloEt2", c.tituloEt2());
        return n;
    }

    private ObjectNode writeParametros(SnapshotParametros p) {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        if (p.porcentajeHerramientaMenor() != null) {
            n.put("porcentajeHerramientaMenor", p.porcentajeHerramientaMenor().toPlainString());
        }
        if (p.porcentajeIndirecto() != null) {
            n.put("porcentajeIndirecto", p.porcentajeIndirecto().toPlainString());
        }
        if (p.iva() != null) {
            n.put("iva", p.iva().toPlainString());
        }
        if (p.moneda() != null) n.put("moneda", p.moneda());
        if (p.mostrarSeccionesVacias() != null) n.put("mostrarSeccionesVacias", p.mostrarSeccionesVacias());
        if (p.sufijosSeccionActivos() != null) n.put("sufijosSeccionActivos", p.sufijosSeccionActivos());
        if (p.mostrarSubtotalesSeccion() != null) n.put("mostrarSubtotalesSeccion", p.mostrarSubtotalesSeccion());
        if (p.mostrarSubtotalesPie() != null) n.put("mostrarSubtotalesPie", p.mostrarSubtotalesPie());
        if (p.mostrarNombreProyectoHeader() != null)
            n.put("mostrarNombreProyectoHeader", p.mostrarNombreProyectoHeader());
        if (p.enumerarApus() != null) n.put("enumerarApus", p.enumerarApus());
        if (p.mensajeFooter() != null) n.put("mensajeFooter", p.mensajeFooter());
        if (p.modoCodigoRubro() != null) n.put("modoCodigoRubro", p.modoCodigoRubro());
        return n;
    }

    private ObjectNode writeTitulos(SnapshotTitulos t) {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        if (t.tituloEt1() != null) n.put("tituloEt1", t.tituloEt1());
        if (t.tituloEt2() != null) n.put("tituloEt2", t.tituloEt2());
        return n;
    }

    private ObjectNode writeCapitulo(SnapshotCapitulo c) {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.put("item", c.item());
        if (c.descripcion() != null) n.put("descripcion", c.descripcion());
        n.put("orden", c.orden);
        if (c.parentItem() != null) n.put("parentItem", c.parentItem());
        if (c.hijos() != null && !c.hijos().isEmpty()) {
            ArrayNode hijos = JsonNodeFactory.instance.arrayNode();
            for (SnapshotCapitulo h : c.hijos()) {
                hijos.add(writeCapitulo(h));
            }
            n.set("hijos", hijos);
        }
        if (c.rubros() != null && !c.rubros().isEmpty()) {
            ArrayNode rubros = JsonNodeFactory.instance.arrayNode();
            for (SnapshotRubro r : c.rubros()) {
                ObjectNode rn = JsonNodeFactory.instance.objectNode();
                rn.put("item", r.item());
                if (r.codigo() != null) rn.put("codigo", r.codigo());
                if (r.descripcion() != null) rn.put("descripcion", r.descripcion());
                if (r.unidad() != null) rn.put("unidad", r.unidad());
                if (r.apu() != null) rn.set("apu", writeApu(r.apu()));
                rubros.add(rn);
            }
            n.set("rubros", rubros);
        }
        return n;
    }

    private ObjectNode writeApu(SnapshotApuEstructural a) {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        if (a.codigo() != null) n.put("codigo", a.codigo());
        if (a.descripcion() != null) n.put("descripcion", a.descripcion());
        if (a.unidad() != null) n.put("unidad", a.unidad());
        if (a.filas() != null && !a.filas().isEmpty()) {
            ArrayNode filas = JsonNodeFactory.instance.arrayNode();
            for (SnapshotFilaApu f : a.filas()) {
                ObjectNode fn = JsonNodeFactory.instance.objectNode();
                if (f.seccionTipo() != null) fn.put("seccionTipo", f.seccionTipo());
                if (f.esHerramientaMenor()) {
                    fn.put("esHerramientaMenor", true);
                } else {
                    if (f.insumoCodigo() != null) fn.put("insumoCodigo", f.insumoCodigo());
                    if (f.cantidad() != null) fn.put("cantidad", f.cantidad().toPlainString());
                    if (f.rendimiento() != null)
                        fn.put("rendimiento", f.rendimiento().toPlainString());
                }
                filas.add(fn);
            }
            n.set("filas", filas);
        }
        return n;
    }

    // =========================================================================
    // READER (lenient: ignora campos extra, incluyendo precios/cantidades heredados)
    // =========================================================================

    public Snapshot leer(String jsonb) {
        if (jsonb == null || jsonb.isBlank()) {
            return new Snapshot(null, null, null, List.of());
        }
        try {
            JsonNode root = objectMapper.readTree(jsonb);
            SnapshotCabecera cabecera = leerCabecera(root);
            SnapshotTitulos titulosLegacy = leerTitulos(root);
            // tituloEt1/Et2 también pueden venir del bloque legacy `titulos`
            // (snapshots previos a la cabecera canónica). Cabecera gana si
            // está presente; en su defecto, se conserva lo que venga de
            // `titulos` para no perder la fila de export.
            if (cabecera == null && titulosLegacy != null) {
                cabecera = new SnapshotCabecera(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        titulosLegacy.tituloEt1(),
                        titulosLegacy.tituloEt2());
            } else if (cabecera != null
                    && (cabecera.tituloEt1() == null || cabecera.tituloEt2() == null)
                    && titulosLegacy != null) {
                cabecera = new SnapshotCabecera(
                        cabecera.codigo(),
                        cabecera.descripcion(),
                        cabecera.anio(),
                        cabecera.fechaInicio(),
                        cabecera.plazoEjecucion(),
                        cabecera.plazoUnidad(),
                        cabecera.direccionInstitucional(),
                        cabecera.subdireccionInstitucional(),
                        cabecera.tituloEt1() != null ? cabecera.tituloEt1() : titulosLegacy.tituloEt1(),
                        cabecera.tituloEt2() != null ? cabecera.tituloEt2() : titulosLegacy.tituloEt2());
            }
            return new Snapshot(cabecera, leerParametros(root), titulosLegacy, leerCapitulos(root));
        } catch (Exception e) {
            throw new RuntimeException("snapshot_estructura corrupto: " + e.getMessage(), e);
        }
    }

    private SnapshotCabecera leerCabecera(JsonNode root) {
        JsonNode n = root.get("cabecera");
        if (n == null || n.isNull()) return null;
        Short anio =
                n.has("anio") && !n.get("anio").isNull() ? (short) n.get("anio").asInt() : null;
        Short plazo = n.has("plazoEjecucion") && !n.get("plazoEjecucion").isNull()
                ? (short) n.get("plazoEjecucion").asInt()
                : null;
        LocalDate fechaInicio = null;
        JsonNode fecha = n.get("fechaInicio");
        if (fecha != null && !fecha.isNull() && fecha.isTextual()) {
            String s = fecha.asText();
            if (s != null && !s.isBlank()) {
                try {
                    fechaInicio = LocalDate.parse(s);
                } catch (Exception ignored) {
                    // fecha malformada → null (el servicio cae al default).
                }
            }
        }
        return new SnapshotCabecera(
                textOrNull(n, "codigo"),
                textOrNull(n, "descripcion"),
                anio,
                fechaInicio,
                plazo,
                textOrNull(n, "plazoUnidad"),
                textOrNull(n, "direccionInstitucional"),
                textOrNull(n, "subdireccionInstitucional"),
                textOrNull(n, "tituloEt1"),
                textOrNull(n, "tituloEt2"));
    }

    private SnapshotParametros leerParametros(JsonNode root) {
        JsonNode n = root.get("parametros");
        if (n == null || n.isNull()) return null;
        return new SnapshotParametros(
                decimalOrNull(n, "porcentajeHerramientaMenor"),
                decimalOrNull(n, "porcentajeIndirecto"),
                decimalOrNull(n, "iva"),
                textOrNull(n, "moneda"),
                boolOrNull(n, "mostrarSeccionesVacias"),
                boolOrNull(n, "sufijosSeccionActivos"),
                boolOrNull(n, "mostrarSubtotalesSeccion"),
                boolOrNull(n, "mostrarSubtotalesPie"),
                boolOrNull(n, "mostrarNombreProyectoHeader"),
                boolOrNull(n, "enumerarApus"),
                textOrNull(n, "mensajeFooter"),
                textOrNull(n, "modoCodigoRubro"));
    }

    private SnapshotTitulos leerTitulos(JsonNode root) {
        JsonNode n = root.get("titulos");
        if (n == null || n.isNull()) return null;
        return new SnapshotTitulos(textOrNull(n, "tituloEt1"), textOrNull(n, "tituloEt2"));
    }

    private List<SnapshotCapitulo> leerCapitulos(JsonNode root) {
        JsonNode caps = root.get("capitulos");
        if (caps == null || !caps.isArray()) return List.of();
        List<SnapshotCapitulo> out = new ArrayList<>();
        for (JsonNode cap : caps) {
            out.add(leerCapitulo(cap));
        }
        return out;
    }

    private SnapshotCapitulo leerCapitulo(JsonNode n) {
        String item = textOrNull(n, "item");
        String descripcion = textOrNull(n, "descripcion");
        int orden = n.path("orden").asInt(1);
        String parentItem = textOrNull(n, "parentItem");
        List<SnapshotCapitulo> hijos = new ArrayList<>();
        JsonNode hijosNode = n.get("hijos");
        if (hijosNode != null && hijosNode.isArray()) {
            for (JsonNode h : hijosNode) {
                hijos.add(leerCapitulo(h));
            }
        }
        List<SnapshotRubro> rubros = new ArrayList<>();
        JsonNode rubrosNode = n.get("rubros");
        if (rubrosNode != null && rubrosNode.isArray()) {
            for (JsonNode r : rubrosNode) {
                SnapshotRubro rubro = leerRubro(r);
                if (rubro != null) rubros.add(rubro);
            }
        }
        return new SnapshotCapitulo(item, descripcion, orden, parentItem, hijos, rubros);
    }

    private SnapshotRubro leerRubro(JsonNode n) {
        String item = textOrNull(n, "item");
        if (item == null) return null;
        JsonNode apuNode = n.get("apu");
        SnapshotApuEstructural apu = apuNode == null ? null : leerApu(apuNode);
        return new SnapshotRubro(
                item, textOrNull(n, "codigo"), textOrNull(n, "descripcion"), textOrNull(n, "unidad"), apu);
    }

    private SnapshotApuEstructural leerApu(JsonNode n) {
        String codigo = textOrNull(n, "codigo");
        String descripcion = textOrNull(n, "descripcion");
        String unidad = textOrNull(n, "unidad");
        List<SnapshotFilaApu> filas = new ArrayList<>();
        JsonNode filasNode = n.get("filas");
        if (filasNode != null && filasNode.isArray()) {
            for (JsonNode f : filasNode) {
                filas.add(leerFilaApu(f));
            }
        }
        return new SnapshotApuEstructural(codigo, descripcion, unidad, filas);
    }

    private SnapshotFilaApu leerFilaApu(JsonNode n) {
        boolean hm = n.path("esHerramientaMenor").asBoolean(false);
        String seccionTipo = textOrNull(n, "seccionTipo");
        String insumoCodigo = textOrNull(n, "insumoCodigo");
        BigDecimal cantidad = decimalOrNull(n, "cantidad");
        BigDecimal rendimiento = decimalOrNull(n, "rendimiento");
        // Ignoramos silenciosamente los campos heredados del V004 (precios,
        // IDs, log/history) y los auxiliares (orden, descripcion, publicId).
        // El snapshot canónico no transporta precios ni IDs — son irrelevantes
        // al cargar la plantilla (los precios se recalculan desde la base
        // destino; los IDs se regeneran).
        return new SnapshotFilaApu(seccionTipo, hm, insumoCodigo, cantidad, rendimiento);
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

    private static Boolean boolOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) return null;
        if (v.isBoolean()) return v.asBoolean();
        if (v.isTextual()) {
            String s = v.asText();
            if (s == null || s.isBlank()) return null;
            return Boolean.parseBoolean(s);
        }
        return null;
    }

    /** Itera en orden DFS todos los capítulos (raíz → hojas). */
    public List<SnapshotCapitulo> aplanar(Snapshot snap) {
        if (snap == null || snap.capitulos() == null) return List.of();
        List<SnapshotCapitulo> out = new ArrayList<>();
        addAllCapitulos(snap.capitulos(), out);
        return out;
    }

    /** Itera todas las filas APU del snapshot (de todos los rubros → APUs estructurales). */
    public List<SnapshotApuEstructural> apusEstructurales(Snapshot snap) {
        if (snap == null) return List.of();
        List<SnapshotApuEstructural> out = new ArrayList<>();
        for (SnapshotCapitulo c : aplanar(snap)) {
            for (SnapshotRubro r : c.rubros()) {
                if (r.apu() != null) {
                    out.add(r.apu());
                }
            }
        }
        return out;
    }

    /** Iterador en pre-order (DFS) sobre el árbol de capítulos. */
    public Iterator<SnapshotCapitulo> preOrder(Snapshot snap) {
        if (snap == null || snap.capitulos() == null)
            return List.<SnapshotCapitulo>of().iterator();
        return new Iterator<SnapshotCapitulo>() {
            final List<SnapshotCapitulo> all = aplanar(snap);
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < all.size();
            }

            @Override
            public SnapshotCapitulo next() {
                return all.get(i++);
            }
        };
    }

    /** Parseo a JsonNode opaco para devolverlo en el response. */
    public JsonNode parseJson(String jsonb) {
        if (jsonb == null || jsonb.isBlank()) {
            return JsonNodeFactory.instance.objectNode();
        }
        try {
            return objectMapper.readTree(jsonb);
        } catch (Exception e) {
            throw new RuntimeException("snapshot_estructura corrupto: " + e.getMessage(), e);
        }
    }

    private static void addAllCapitulos(List<SnapshotCapitulo> source, List<SnapshotCapitulo> target) {
        for (SnapshotCapitulo c : source) {
            target.add(c);
            if (c.hijos() != null && !c.hijos().isEmpty()) {
                addAllCapitulos(c.hijos(), target);
            }
        }
    }
}
