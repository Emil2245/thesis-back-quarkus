package ec.uce.propuestas.presupuesto.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plan 023 — flujo REST de mutación de rubros (P-29). Cubre:
 * <ul>
 *   <li>Crear rubro bajo un capítulo (POST), incluyendo la propagación
 *       write-through al árbol completo (rubro.precioTotal, capítulo.total,
 *       presupuesto.total) y la respuesta {@link ec.uce.propuestas.presupuesto.dto.PresupuestoResponse}
 *       del árbol recalculado.</li>
 *   <li>D-09 apu-referenciado (409): un APU ya vinculado a otro rubro del
 *       presupuesto del path no se puede vincular de nuevo.</li>
 *   <li>Cross-version APU (400 validacion): un APU de OTRA versión del mismo
 *       proyecto no se acepta.</li>
 *   <li>Cross-presupuesto capítulo (400 validacion): un capítulo que pertenece
 *       a OTRO presupuesto se rechaza.</li>
 *   <li>Frontera UUIDv7 → 400, foreign → 404, owner-scope 404 (RNF-05).</li>
 *   <li>Cantidad inválida (≤ 0, ausente) → 400 validacion (RNF-09).</li>
 *   <li>Editar cantidad (PATCH) propaga el write-through; sólo cambia
 *       {@code cantidad}, deja el resto del rubro intacto.</li>
 *   <li>Eliminar rubro: APU sobrevive (D-09 + V001 §3); los rubros hermanos
 *       compactan sus {@code item} a {@code capitulo.item + "." + 1..n};
 *       rubro prefix stale (riesgo Plan 022) se normaliza antes de devolver.</li>
 *   <li>Mirroring APU: el rubro hereda {@code codigo}, {@code descripcion},
 *       {@code unidad} del APU; su {@code item} se concatena como
 *       {@code capitulo.item + "." + ordinal}.</li>
 * </ul>
 *
 * <p>Convenciones (idénticas a {@code CapituloResourceIT}): el
 * {@code presupuestoId} de path es UUIDv7 del {@code public_id} de la fila
 * {@code presupuesto} (Plan 07 / WU-03); el {@code BIGINT} interno nunca
 * aparece en JSON. La mutación flush + recálculo write-through deja el
 * estado coherente antes de devolver la respuesta.</p>
 */
@QuarkusTest
class RubroResourceIT {

    private static final String UUID_V7 =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

    /** UUIDv4 (no v7) bien formado — usado para verificar la frontera 400. */
    private static final String UUID_NO_V7 = "550e8400-e29b-41d4-a716-446655440000";

    /** UUIDv7 inexistente pero bien formado — usado para verificar 404. */
    private static final String UUID_INEXISTENTE_V7 = "0192f6c4-7c8a-7000-8000-000000000000";

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        mailbox.clear();
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, "
                    + "insumo, base_insumos, parametros_proyecto, firmante, proyecto, "
                    + "token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private String crearProyecto(String token, String nombre) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto",
                        nombre,
                        "codigo",
                        "P-2026-P23",
                        "anio",
                        (short) 2026,
                        "plazoEjecucion",
                        (short) 6,
                        "plazoUnidad",
                        "MES",
                        "direccionInstitucional",
                        "GAD Municipal"))
                .when()
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private Long internalProyectoId(String publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM proyecto WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(publicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private Long internalPresupuestoId(String publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM presupuesto WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(publicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private String vigenteDeProyecto(String proyectoPublicId) throws Exception {
        Long proyectoId = internalProyectoId(proyectoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT public_id FROM presupuesto WHERE proyecto_id = ? AND es_vigente = TRUE")) {
            ps.setLong(1, proyectoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private Long internalApuId(String publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM apu WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(publicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private Long internalCapituloId(String publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM capitulo WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(publicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private Long internalRubroId(String publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM rubro WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(publicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /**
     * Siembra un APU con costo_total conocido ({@code CT_BASE}) y la estructura
     * M/N/O/P vacía (HM con %HM=5% × N=0). El costo total real lo calculará el
     * motor cuando se invoque {@code recalcular}; para los tests que sólo
     * necesitan el vínculo 1:1 basta con que exista.
     */
    private String insertarApu(String presupuestoPublicId, String codigo, String descripcion, String unidad)
            throws Exception {
        Long presupuestoId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("INSERT INTO apu (presupuesto_id, codigo, descripcion, "
                        + "unidad, costo_directo, costo_indirecto, costo_total) "
                        + "VALUES (?, ?, ?, ?, 0, 0, 0) RETURNING public_id")) {
            ps.setLong(1, presupuestoId);
            ps.setString(2, codigo);
            ps.setString(3, descripcion);
            ps.setString(4, unidad);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    /**
     * Inserta una sección + fila de MO con insumo heredado (override NULL).
     * El subtotalN = cantidad × tarifaJornal × rendimiento. Al ejecutar
     * {@code Motor.calcularApu} el HM = 5% × subtotalN se suma al bloque M.
     */
    private void insertarApuConMo(
            String apuPublicId, String insumoCodigo, BigDecimal cantidad, BigDecimal rendimiento, BigDecimal tarifa)
            throws Exception {
        Long apuId = internalApuId(apuPublicId);
        // Crear insumo si no existe en el catálogo del proyecto
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO apu_seccion (apu_id, tipo, subtotal, orden) VALUES (?, 'EQUIPO', 0, 1)")) {
            ps.setLong(1, apuId);
            ps.executeUpdate();
        }
        long moSeccionId;
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO apu_seccion (apu_id, tipo, subtotal, orden) VALUES (?, 'MANO_OBRA', 0, 2) "
                                + "RETURNING id")) {
            ps.setLong(1, apuId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                moSeccionId = rs.getLong(1);
            }
        }
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO apu_seccion (apu_id, tipo, subtotal, orden) VALUES (?, 'MATERIAL', 0, 3)")) {
            ps.setLong(1, apuId);
            ps.executeUpdate();
        }
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO apu_seccion (apu_id, tipo, subtotal, orden) VALUES (?, 'TRANSPORTE', 0, 4)")) {
            ps.setLong(1, apuId);
            ps.executeUpdate();
        }
        // Fila HM canónica (decisión §17 #9): 1ª fila del bloque M
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO apu_detalle (seccion_id, descripcion, orden, es_herramienta_menor, "
                                + "costo_hora, unidad, costo) "
                                + "VALUES ((SELECT id FROM apu_seccion WHERE apu_id = ? AND tipo = 'EQUIPO'), "
                                + "'Herramienta Menor 5%MO', 1, TRUE, 0, '%', 0)")) {
            ps.setLong(1, apuId);
            ps.executeUpdate();
        }
        // Detalle MO referenciando el insumo + override (tarifa_jornal).
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO apu_detalle (seccion_id, insumo_id, descripcion, orden, cantidad, "
                                + "es_herramienta_menor, tarifa_jornal, costo_hora, rendimiento, unidad, costo) "
                                + "VALUES (?, NULL, ?, 1, ?, FALSE, ?, 0, ?, 'h', 0)")) {
            ps.setLong(1, moSeccionId);
            ps.setString(2, insumoCodigo);
            ps.setBigDecimal(3, cantidad);
            ps.setBigDecimal(4, tarifa);
            ps.setBigDecimal(5, rendimiento);
            ps.executeUpdate();
        }
    }

    /** Inserta una versión adicional (no vigente) para tests cross-version. */
    private String insertarVersionNoVigente(String proyectoPublicId, short version) throws Exception {
        Long proyectoId = internalProyectoId(proyectoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("INSERT INTO presupuesto (proyecto_id, version, es_vigente) "
                                + "VALUES (?, ?, FALSE) RETURNING public_id")) {
            ps.setLong(1, proyectoId);
            ps.setShort(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private String capituloPublicIdDeItem(String presupuestoPublicId, String item) throws Exception {
        Long presupuestoId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT public_id FROM capitulo WHERE presupuesto_id = ? AND item = ?")) {
            ps.setLong(1, presupuestoId);
            ps.setString(2, item);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private String crearCapituloRaiz(String token, String presupuestoId, String descripcion) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", descripcion))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201)
                .extract()
                .path("capitulos[0].id");
    }

    private BigDecimal leerRubroCantidad(long rubroId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT cantidad FROM rubro WHERE id = ?")) {
            ps.setLong(1, rubroId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private BigDecimal leerRubroPrecioTotal(long rubroId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT precio_total FROM rubro WHERE id = ?")) {
            ps.setLong(1, rubroId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private BigDecimal leerCapituloTotal(long capituloId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT total FROM capitulo WHERE id = ?")) {
            ps.setLong(1, capituloId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private BigDecimal leerPresupuestoTotal(String presupuestoPublicId) throws Exception {
        Long pId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT total FROM presupuesto WHERE id = ?")) {
            ps.setLong(1, pId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private long contarRubros(String presupuestoPublicId) throws Exception {
        Long pId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM rubro r "
                        + "JOIN capitulo c ON c.id = r.capitulo_id WHERE c.presupuesto_id = ?")) {
            ps.setLong(1, pId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long apuSigueExistiendo(long apuId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM apu WHERE id = ?")) {
            ps.setLong(1, apuId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Huella observable: {@code item:orden} de cada rubro del presupuesto. */
    private List<String> huellaRubros(String presupuestoPublicId) throws Exception {
        Long pId = internalPresupuestoId(presupuestoPublicId);
        List<String> out = new ArrayList<>();
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT r.item FROM rubro r "
                        + "JOIN capitulo c ON c.id = r.capitulo_id "
                        + "WHERE c.presupuesto_id = ? ORDER BY r.item")) {
            ps.setLong(1, pId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Crear rubro: la respuesta devuelve el árbol completo con totales
     * write-through y los campos del rubro espejados del APU.
     */
    @Test
    void TC_P29_01_crear_rubro_propagacion_total_y_mirroring_apu() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p23-c1@ex.com");
        String proyectoId = crearProyecto(token, "Pozo");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String apuId = insertarApu(presupuestoId, "APU-POZO", "Pozo de agua", "m3");
        String capId = crearCapituloRaiz(token, presupuestoId, "OBRAS PRELIMINARES");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuId, "cantidad", "125.000000"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(201)
                .body("presupuestoId", equalTo(presupuestoId))
                .body("totalGeneral", equalTo("0.000000"))
                .body("capitulos.size()", is(1))
                .body("capitulos[0].id", equalTo(capId))
                .body("capitulos[0].item", equalTo("1"))
                .body("capitulos[0].rubros.size()", is(1))
                .body("capitulos[0].rubros[0].codigo", equalTo("APU-POZO"))
                .body("capitulos[0].rubros[0].descripcion", equalTo("Pozo de agua"))
                .body("capitulos[0].rubros[0].unidad", equalTo("m3"))
                .body("capitulos[0].rubros[0].cantidad", equalTo("125.000000"))
                .body("capitulos[0].rubros[0].item", equalTo("1.1"))
                .body("capitulos[0].rubros[0].apuId", equalTo(apuId))
                .body("capitulos[0].rubros[0].id", matchesPattern(UUID_V7));

        // Persistencia: rubro espejado correctamente
        String rubroItem = "1.1";
        String rubroPublicId = readRubroPublicIdPorItem(presupuestoId, rubroItem);
        assertNotNull(rubroPublicId);
        long rubroId = internalRubroId(rubroPublicId);
        assertEquals(0, leerRubroCantidad(rubroId).compareTo(new BigDecimal("125.000000")));
        // El rubro espeja codigo/descripcion/unidad del APU
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT codigo, descripcion, unidad FROM rubro WHERE id = ?")) {
            ps.setLong(1, rubroId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals("APU-POZO", rs.getString(1));
                assertEquals("Pozo de agua", rs.getString(2));
                assertEquals("m3", rs.getString(3));
            }
        }
    }

    /** D-09: crear un segundo rubro con el mismo APU → 409 apu-referenciado. */
    @Test
    void TC_P29_02_apu_ya_referenciado_en_otro_rubro_devuelve_409() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p23-c2@ex.com");
        String proyectoId = crearProyecto(token, "D-09");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String apuId = insertarApu(presupuestoId, "APU-DUP", "Duplicado", "u");
        String capId = crearCapituloRaiz(token, presupuestoId, "Capitulo A");

        // Primer rubro bajo el capítulo A — OK
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuId, "cantidad", "10.000000"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(201);

        // Intentar crear otro rubro con el mismo APU → 409 apu-referenciado
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuId, "cantidad", "5.000000"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(409)
                .body("codigo", equalTo("apu-referenciado"));
    }

    /** cantidad ≤ 0 → 400 validacion. */
    @Test
    void TC_P29_03_cantidad_cero_o_negativa_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p23-c3@ex.com");
        String proyectoId = crearProyecto(token, "Cantidad invalida");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String apuId = insertarApu(presupuestoId, "APU-INV", "Inv", "u");
        String capId = crearCapituloRaiz(token, presupuestoId, "Capitulo");

        // cantidad = 0
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuId, "cantidad", "0"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        // cantidad negativa
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuId, "cantidad", "-5.000000"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        // cantidad omitida → validación de @NotNull
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuId))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    /** Cross-version: APU pertenece a OTRA versión del mismo proyecto → 400 validacion. */
    @Test
    void TC_P29_04_apu_de_otra_version_del_mismo_proyecto_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p23-c4@ex.com");
        String proyectoId = crearProyecto(token, "Cross version");
        String vigenteId = vigenteDeProyecto(proyectoId);
        String v2Id = insertarVersionNoVigente(proyectoId, (short) 2);

        String apuVigente = insertarApu(vigenteId, "APU-VIG", "APU v1", "u");
        String apuV2 = insertarApu(v2Id, "APU-V2", "APU v2", "u");
        String capVigente = crearCapituloRaiz(token, vigenteId, "Cap v1");

        // APU v2 → no debe vincularse a rubro de la versión vigente
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuV2, "cantidad", "10.000000"))
                .when()
                .post("/api/v1/presupuestos/" + vigenteId + "/capitulos/" + capVigente + "/rubros")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        // APU v1 sigue siendo válido para la versión vigente
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuVigente, "cantidad", "10.000000"))
                .when()
                .post("/api/v1/presupuestos/" + vigenteId + "/capitulos/" + capVigente + "/rubros")
                .then()
                .statusCode(201);
    }

    /** Cross-presupuesto: capítulo de otro presupuesto → 400 validacion. */
    @Test
    void TC_P29_05_capitulo_de_otro_presupuesto_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p23-c5@ex.com");
        String proyectoA = crearProyecto(token, "Proyecto A");
        String proyectoB = crearProyecto(token, "Proyecto B");
        String presupuestoA = vigenteDeProyecto(proyectoA);
        String presupuestoB = vigenteDeProyecto(proyectoB);

        String apuA = insertarApu(presupuestoA, "APU-A", "APU A", "u");
        String capB = crearCapituloRaiz(token, presupuestoB, "Cap B");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuA, "cantidad", "10.000000"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoB + "/capitulos/" + capB + "/rubros")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    /** Path UUIDv7 malformado / no-v7 → 400 validacion. */
    @Test
    void TC_P29_06_path_uuid_invalido_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p23-c6@ex.com");
        String proyectoId = crearProyecto(token, "Bad UUID");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        // presupuestoId no-v7
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", UUID_NO_V7, "cantidad", "10"))
                .when()
                .post("/api/v1/presupuestos/" + UUID_NO_V7 + "/capitulos/" + UUID_NO_V7 + "/rubros")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        // apuId no-v7
        String apuId = insertarApu(presupuestoId, "APU-NV7", "A", "u");
        String capId = crearCapituloRaiz(token, presupuestoId, "Cap");
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", UUID_NO_V7, "cantidad", "10"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        // PATCH con rubroId no-v7
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("cantidad", "10"))
                .when()
                .patch("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros/" + UUID_NO_V7)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        // DELETE con rubroId no-v7
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros/" + UUID_NO_V7)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    /** Recursos ajenos o inexistentes → 404 no-encontrado (RNF-05: nunca 403). */
    @Test
    void TC_P29_07_recursos_ajenos_o_inexistentes_devuelven_404() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p23-c7-owner@ex.com");
        String proyectoId = crearProyecto(token, "Owner");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String apuId = insertarApu(presupuestoId, "APU-OW", "A", "u");
        String capId = crearCapituloRaiz(token, presupuestoId, "Cap");

        // Presupuesto inexistente (UUIDv7 bien formado pero no creado) → 404
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuId, "cantidad", "10"))
                .when()
                .post("/api/v1/presupuestos/" + UUID_INEXISTENTE_V7 + "/capitulos/" + UUID_INEXISTENTE_V7 + "/rubros")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        // Capítulo inexistente en presupuesto existente → 404
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuId, "cantidad", "10"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + UUID_INEXISTENTE_V7 + "/rubros")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        // PATCH / DELETE sobre rubro inexistente → 404
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("cantidad", "10"))
                .when()
                .patch("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros/"
                        + UUID_INEXISTENTE_V7)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros/"
                        + UUID_INEXISTENTE_V7)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    /** Owner-scope: un caller ajeno al presupuesto del path → 404 (nunca 403). */
    @Test
    void TC_P29_08_caller_ajeno_obtiene_404_nunca_403() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p23-c8-owner@ex.com");
        String proyectoId = crearProyecto(token, "Owner");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String apuId = insertarApu(presupuestoId, "APU-INT", "A", "u");
        String capId = crearCapituloRaiz(token, presupuestoId, "Cap");

        String intruso = AuthSupport.registrarConToken(mailbox, "p23-c8-intruso@ex.com");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + intruso)
                .body(Map.of("apuId", apuId, "cantidad", "10"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    /**
     * Editar cantidad: propaga el write-through; deja intactos codigo/descripcion/unidad/item.
     */
    @Test
    void TC_P29_09_patch_cantidad_propagacion_y_otros_campos_intactos() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p23-c9@ex.com");
        String proyectoId = crearProyecto(token, "Patch");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String apuId = insertarApu(presupuestoId, "APU-PATCH", "Patch me", "kg");
        String capId = crearCapituloRaiz(token, presupuestoId, "Cap");

        String rubroId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuId, "cantidad", "10.000000"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(201)
                .extract()
                .path("capitulos[0].rubros[0].id");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("cantidad", "200.000000"))
                .when()
                .patch("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros/" + rubroId)
                .then()
                .statusCode(200)
                .body("presupuestoId", equalTo(presupuestoId))
                .body("capitulos[0].rubros.size()", is(1))
                .body("capitulos[0].rubros[0].id", equalTo(rubroId))
                .body("capitulos[0].rubros[0].cantidad", equalTo("200.000000"))
                // Otros campos no cambian
                .body("capitulos[0].rubros[0].codigo", equalTo("APU-PATCH"))
                .body("capitulos[0].rubros[0].descripcion", equalTo("Patch me"))
                .body("capitulos[0].rubros[0].unidad", equalTo("kg"))
                .body("capitulos[0].rubros[0].item", equalTo("1.1"))
                .body("capitulos[0].rubros[0].apuId", equalTo(apuId));

        // Persistencia: la cantidad quedó en 200.000000; el resto intacto
        long rubroInternoId = internalRubroId(rubroId);
        assertEquals(0, leerRubroCantidad(rubroInternoId).compareTo(new BigDecimal("200.000000")));
    }

    /** PATCH con cantidad ≤ 0 → 400 validacion. */
    @Test
    void TC_P29_10_patch_cantidad_invalida_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p23-c10@ex.com");
        String proyectoId = crearProyecto(token, "Patch inv");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String apuId = insertarApu(presupuestoId, "APU-PAT", "A", "u");
        String capId = crearCapituloRaiz(token, presupuestoId, "Cap");

        String rubroId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuId, "cantidad", "10"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(201)
                .extract()
                .path("capitulos[0].rubros[0].id");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("cantidad", "0"))
                .when()
                .patch("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros/" + rubroId)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("cantidad", "-1"))
                .when()
                .patch("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros/" + rubroId)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    /**
     * DELETE: el rubro se elimina; el APU sobrevive (D-09 + V001 §3); los rubros
     * hermanos del mismo capítulo compactan sus {@code item} a
     * {@code capitulo.item + "." + 1..n}.
     */
    @Test
    void TC_P29_11_delete_deja_apu_y_compacta_items_hermanos() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p23-c11@ex.com");
        String proyectoId = crearProyecto(token, "Delete compact");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String apu1 = insertarApu(presupuestoId, "APU-1", "Rubro 1", "u");
        String apu2 = insertarApu(presupuestoId, "APU-2", "Rubro 2", "u");
        String apu3 = insertarApu(presupuestoId, "APU-3", "Rubro 3", "u");
        String capId = crearCapituloRaiz(token, presupuestoId, "Capitulo");

        // Crear 3 rubros bajo el capítulo "1" → items "1.1", "1.2", "1.3"
        for (String a : List.of(apu1, apu2, apu3)) {
            given().contentType(JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(Map.of("apuId", a, "cantidad", "5"))
                    .when()
                    .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                    .then()
                    .statusCode(201);
        }
        assertEquals(List.of("1.1", "1.2", "1.3"), huellaRubros(presupuestoId));

        long apu2Id = internalApuId(apu2);
        String rubroMedioId = readRubroPublicIdPorItem(presupuestoId, "1.2");

        // Eliminar el rubro del medio (item "1.2")
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros/" + rubroMedioId)
                .then()
                .statusCode(200)
                .body("capitulos[0].rubros.size()", is(2))
                .body("capitulos[0].rubros[0].item", equalTo("1.1"))
                .body("capitulos[0].rubros[0].codigo", equalTo("APU-1"))
                .body("capitulos[0].rubros[1].item", equalTo("1.2"))
                .body("capitulos[0].rubros[1].codigo", equalTo("APU-3"));

        // Persistencia: rubros compactados a "1.1" + "1.2" (el antiguo "1.3" pasó a "1.2")
        assertEquals(List.of("1.1", "1.2"), huellaRubros(presupuestoId));
        // El APU del medio sigue existiendo (D-09 + V001 §3)
        assertEquals(1L, apuSigueExistiendo(apu2Id));
    }

    /**
     * Plan 022 riesgo: si un rubro conserva un item stale
     * ({@code capitulo.item + "." + N} donde N ya no corresponde), la
     * compactación en este plan debe normalizar antes de devolver el árbol.
     * Verificamos que tras un POST + DELETE (que rompe la contigüidad) los
     * items quedan contiguos {@code capitulo.item + "." + 1..n}.
     */
    @Test
    void TC_P29_12_normaliza_items_stale_del_capitulo_antes_de_devolver() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p23-c12@ex.com");
        String proyectoId = crearProyecto(token, "Stale items");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String apu1 = insertarApu(presupuestoId, "APU-A", "A", "u");
        String apu2 = insertarApu(presupuestoId, "APU-B", "B", "u");
        String apu3 = insertarApu(presupuestoId, "APU-C", "C", "u");
        String capId = crearCapituloRaiz(token, presupuestoId, "Cap");

        // Crear 3 rubros
        for (String a : List.of(apu1, apu2, apu3)) {
            given().contentType(JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(Map.of("apuId", a, "cantidad", "5"))
                    .when()
                    .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                    .then()
                    .statusCode(201);
        }

        // Eliminar el primero (item "1.1") — el "1.2" y "1.3" deben pasar a "1.1" y "1.2"
        String rubro1Id = readRubroPublicIdPorItem(presupuestoId, "1.1");
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros/" + rubro1Id)
                .then()
                .statusCode(200);

        // Persistencia: huella "1.1", "1.2" contigua
        assertEquals(List.of("1.1", "1.2"), huellaRubros(presupuestoId));
    }

    /**
     * Write-through: el {@code precioTotal} del rubro se calcula a partir del
     * costo_total del APU y la cantidad (regla workbook-consistent).
     * Verificación contra BD, no sólo contra el response.
     */
    @Test
    void TC_P29_13_write_through_precio_total_rubro_y_capitulo_y_presupuesto() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p23-c13@ex.com");
        String proyectoId = crearProyecto(token, "WT");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        // APU con MO cantidad=2, rendimiento=1, tarifaJornal=4 → subtotalN=8; HM=0.4; CD=8.4
        // %CI default del proyecto = 0 (ParametrosProyecto.porcentajeIndirecto = null)
        // → CT = 8.4; PU_2dp_DOWN(8.4) = 8.40
        String apuId = insertarApu(presupuestoId, "APU-WT", "WT", "u");
        insertarApuConMo(apuId, "MO-001", new BigDecimal("2"), new BigDecimal("1"), new BigDecimal("4.00"));

        // Necesitamos disparar el recalcular del APU para que su CT quede en 8.4
        // Como Plan023 ya lo hace vía POST (la mutación del rubro recalcula
        // toda la versión), basta con crear el rubro y leer el resultado.
        String capId = crearCapituloRaiz(token, presupuestoId, "Cap");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuId, "cantidad", "3.000000"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(201);

        // Persistencia: PT = 3 × 8.40 = 25.20 a escala 6 = 25.200000
        String rubroId = readRubroPublicIdPorItem(presupuestoId, "1.1");
        long rubroInternoId = internalRubroId(rubroId);
        BigDecimal pt = leerRubroPrecioTotal(rubroInternoId).setScale(6, java.math.RoundingMode.HALF_UP);
        assertEquals(0, new BigDecimal("25.200000").compareTo(pt), "precio_total = 3 × 8.40 = 25.20 (escala 6)");

        long capInternoId = internalCapituloId(capId);
        assertEquals(
                0,
                new BigDecimal("25.200000")
                        .compareTo(leerCapituloTotal(capInternoId).setScale(6, java.math.RoundingMode.HALF_UP)));
        assertEquals(
                0,
                new BigDecimal("25.200000")
                        .compareTo(leerPresupuestoTotal(presupuestoId).setScale(6, java.math.RoundingMode.HALF_UP)));
    }

    /**
     * %CI del proyecto no cero: el costoTotal del APU y el rubro.precioTotal se
     * ajustan por el porcentaje indirecto (write-through por la mutación del rubro).
     */
    @Test
    void TC_P29_14_write_through_aplica_ci_del_proyecto() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p23-c14@ex.com");
        String proyectoId = crearProyecto(token, "CI");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        // %CI = 0.18 a nivel proyecto
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO parametros_proyecto (proyecto_id, porcentaje_herramienta_menor, "
                                + "porcentaje_indirecto, iva, moneda) "
                                + "VALUES ((SELECT id FROM proyecto WHERE public_id = ?), 0.05, 0.18, 0.15, 'USD')")) {
            ps.setObject(1, UUID.fromString(proyectoId));
            ps.executeUpdate();
        }

        // APU con subtotalN = 8 → HM = 0.4 → CD = 8.4
        // CI = 8.4 × 0.18 = 1.512 → CT = 9.912
        // PU_2dp_DOWN(9.912) = 9.91; PT = 5 × 9.91 = 49.55
        String apuId = insertarApu(presupuestoId, "APU-CI", "CI", "u");
        insertarApuConMo(apuId, "MO-002", new BigDecimal("2"), new BigDecimal("1"), new BigDecimal("4.00"));
        String capId = crearCapituloRaiz(token, presupuestoId, "Cap");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuId, "cantidad", "5.000000"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(201);

        String rubroId = readRubroPublicIdPorItem(presupuestoId, "1.1");
        long rubroInternoId = internalRubroId(rubroId);
        assertEquals(
                0,
                new BigDecimal("49.550000")
                        .compareTo(leerRubroPrecioTotal(rubroInternoId).setScale(6, java.math.RoundingMode.HALF_UP)));
    }

    /** Apéndice al final: el ordinal del item crece monotónicamente al crear más rubros. */
    @Test
    void TC_P29_15_append_only_ordinal_creciente() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p23-c15@ex.com");
        String proyectoId = crearProyecto(token, "Append only");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String apuA = insertarApu(presupuestoId, "APU-AA", "AA", "u");
        String apuB = insertarApu(presupuestoId, "APU-BB", "BB", "u");
        String apuC = insertarApu(presupuestoId, "APU-CC", "CC", "u");
        String capId = crearCapituloRaiz(token, presupuestoId, "Cap");

        // 1er rubro → "1.1", 2º → "1.2", 3º → "1.3"
        for (int i = 0; i < 3; i++) {
            given().contentType(JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(Map.of("apuId", List.of(apuA, apuB, apuC).get(i), "cantidad", "5"))
                    .when()
                    .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                    .then()
                    .statusCode(201)
                    .body("capitulos[0].rubros[" + i + "].item", notNullValue());
        }
        assertEquals(List.of("1.1", "1.2", "1.3"), huellaRubros(presupuestoId));
    }

    /** Cruce: rubro de un capítulo A usado en URL con un capítulo B del mismo presupuesto → 400. */
    @Test
    void TC_P29_16_rubro_de_otro_capitulo_devuelve_400_en_patch_delete() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p23-c16@ex.com");
        String proyectoId = crearProyecto(token, "Cross cap");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String apu1 = insertarApu(presupuestoId, "APU-X", "X", "u");
        String apu2 = insertarApu(presupuestoId, "APU-Y", "Y", "u");

        // 2 capítulos raíz
        String cap1Id = crearCapituloRaiz(token, presupuestoId, "Cap 1");
        String cap2Id = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "Cap 2", "orden", 2))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201)
                .extract()
                .path("capitulos[1].id");

        // Rubro bajo cap1
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apu1, "cantidad", "10"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + cap1Id + "/rubros")
                .then()
                .statusCode(201);

        // Intentar PATCH sobre ese rubro pero con cap2 en el path → 400 validacion
        String rubroCap1 = readRubroPublicIdPorItem(presupuestoId, "1.1");
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("cantidad", "20"))
                .when()
                .patch("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + cap2Id + "/rubros/" + rubroCap1)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        // DELETE sobre ese rubro pero con cap2 en el path → 400 validacion
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + cap2Id + "/rubros/" + rubroCap1)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    /** Item ordinal del rubro respeta el prefijo del capítulo padre (subcapítulo). */
    @Test
    void TC_P29_17_item_concatena_prefijo_del_capitulo_padre() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p23-c17@ex.com");
        String proyectoId = crearProyecto(token, "Prefijo");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String apuId = insertarApu(presupuestoId, "APU-PFX", "Prefijo", "u");

        // Raíz "1", sub "1.1", rubro bajo "1.1" → item "1.1.1"
        String rootId = crearCapituloRaiz(token, presupuestoId, "Raíz");
        String subId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "Sub", "parentId", rootId))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201)
                .extract()
                .path("capitulos[0].subcapitulos[0].id");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuId, "cantidad", "10"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + subId + "/rubros")
                .then()
                .statusCode(201)
                .body("capitulos[0].subcapitulos[0].rubros[0].item", equalTo("1.1.1"));
    }

    /** GET summary: solo lectura. No crea ni muta rubros. */
    @Test
    void TC_P29_18_resumen_no_escribe_y_devuelve_forma_estable() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p23-c18@ex.com");
        String proyectoId = crearProyecto(token, "Resumen read");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        long rubrosAntes = contarRubros(presupuestoId);

        // GET /resumen sin rubros
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/resumen")
                .then()
                .statusCode(200)
                .body("totalGeneral", equalTo("0.000000"))
                .body("ivaReferencial", equalTo("0.000000"))
                .body("totalConIva", equalTo("0.000000"))
                .body("porComponente.EQUIPO", equalTo("0.000000"))
                .body("porComponente.MANO_OBRA", equalTo("0.000000"))
                .body("porComponente.MATERIAL", equalTo("0.000000"))
                .body("porComponente.TRANSPORTE", equalTo("0.000000"));

        // El GET no crea rubros
        long rubrosDespues = contarRubros(presupuestoId);
        assertTrue(rubrosAntes == rubrosDespues, "GET /resumen no muta rubros");
    }

    /** UUID del apu en body no v7 → 400 validacion (controlado por UuidV7.parse). */
    @Test
    void TC_P29_19_apu_uuid_no_v7_en_body_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p23-c19@ex.com");
        String proyectoId = crearProyecto(token, "Apu no v7");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String capId = crearCapituloRaiz(token, presupuestoId, "Cap");

        // apuId no-v7 (UUIDv4)
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", UUID_NO_V7, "cantidad", "10"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Plan 029 (P-34) — sincronización 1:1 rubro↔actividad en el recálculo
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Crear un rubro a través del endpoint POST en un presupuesto con
     * cronograma pre-existente debe crear su actividad automáticamente
     * porque la sincronización central corre al final del recálculo de
     * versión que invoca {@code RubroService.crear()}.
     */
    @Test
    void crear_rubro_con_cronograma_crea_actividad_en_recálculo() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-sync-crear@ex.com");
        String proyectoId = crearProyecto(token, "P29 sync crear");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        String capId = crearCapituloRaiz(token, presupuestoId, "Capitulo unico");
        String apuPublicId = insertarApu(presupuestoId, "APU-1", "APU", "u");

        // Crear cronograma primero (con 0 rubros)
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "SEMANA", "numeroPeriodos", 4))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(201)
                .body("actividades", hasSize(0));

        long antes = contarActividadesSync(presupuestoId);
        assertEquals(0L, antes);

        // POST del rubro bajo el capítulo
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuPublicId, "cantidad", "10"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(201);

        long despues = contarActividadesSync(presupuestoId);
        assertEquals(1L, despues, "1 actividad creada para 1 rubro (cobertura 1:1 tras sync central)");
    }

    /**
     * Borrar un rubro a través del endpoint DELETE en un presupuesto
     * con cronograma pre-existente debe eliminar la actividad por FK
     * CASCADE; la sincronización no requiere borrado explícito.
     */
    @Test
    void eliminar_rubro_con_cronograma_borra_actividad_por_fk_cascade() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-sync-del@ex.com");
        String proyectoId = crearProyecto(token, "P29 sync del");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        String capId = crearCapituloRaiz(token, presupuestoId, "Capitulo unico");
        String apuPublicId = insertarApu(presupuestoId, "APU-1", "APU", "u");

        // Alta del rubro primero
        String rubroPublicId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuPublicId, "cantidad", "5"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(201)
                .extract()
                .path("capitulos[0].rubros[0].id");

        // Crear el cronograma (las actividades se generan vía alta de
        // cronograma; el rubro ya tiene su actividad por autoimport al
        // crear el cronograma).
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "SEMANA", "numeroPeriodos", 4))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(201)
                .body("actividades", hasSize(1));

        assertEquals(1L, contarActividadesSync(presupuestoId));

        // Borrar el rubro.
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros/" + rubroPublicId)
                .then()
                .statusCode(200)
                .body("presupuestoId", equalTo(presupuestoId));

        // Actividad borrada por la FK CASCADE.
        assertEquals(0L, contarActividadesSync(presupuestoId), "FK CASCADE elimina la actividad huérfana");
    }

    /** Helper: cuenta actividades del cronograma de un presupuesto (uso sync IT). */
    private long contarActividadesSync(String presupuestoPublicId) throws Exception {
        Long pId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM actividad a "
                        + "JOIN cronograma c ON c.id = a.cronograma_id WHERE c.presupuesto_id = ?")) {
            ps.setLong(1, pId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private String readRubroPublicIdPorItem(String presupuestoPublicId, String item) throws Exception {
        Long pId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT r.public_id FROM rubro r "
                        + "JOIN capitulo c ON c.id = r.capitulo_id "
                        + "WHERE c.presupuesto_id = ? AND r.item = ?")) {
            ps.setLong(1, pId);
            ps.setString(2, item);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }
}
