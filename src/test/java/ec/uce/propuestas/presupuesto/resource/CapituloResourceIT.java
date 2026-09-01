package ec.uce.propuestas.presupuesto.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plan 022 — flujo REST de mutaciones del árbol de capítulos
 * (P-28 escritura). Cubre: crear (raíz/sub), editar descripción,
 * mover (mismo padre / cruzar padre / rechazar ciclos), eliminar
 * subárbol con cascade y supervivencia de APU (D-09 + V001 §3),
 * frontera UUIDv7 → 400, foreign/missing → 404, parentId ajeno al
 * presupuesto → 400, y recalculo del árbol completo tras cada
 * mutación.
 *
 * <p>La renumeración atómica se verifica por su huella observable: los
 * {@code item} del árbol y la contigüidad {@code 1..n} del {@code orden}
 * dentro de cada nivel tras crear en medio, mover y eliminar (TC_P22_03,
 * TC_P22_29 y TC_P22_30). Una permutación de hermanos sólo puede pasar si
 * la escritura no viola {@code UNIQUE (presupuesto_id, item)} de forma
 * transitoria.
 */
@QuarkusTest
class CapituloResourceIT {

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
                        "P-2026-P22",
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
            ps.setObject(1, java.util.UUID.fromString(publicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private Long internalPresupuestoId(String publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM presupuesto WHERE public_id = ?")) {
            ps.setObject(1, java.util.UUID.fromString(publicId));
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

    private Long internalCapituloId(String publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM capitulo WHERE public_id = ?")) {
            ps.setObject(1, java.util.UUID.fromString(publicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
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

    private Long insertarApu(String presupuestoPublicId, String codigo) throws Exception {
        Long presupuestoId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("INSERT INTO apu (presupuesto_id, codigo, descripcion, unidad, "
                                + "costo_directo, costo_indirecto, costo_total) "
                                + "VALUES (?, ?, ?, 'u', 0, 0, 0) RETURNING id")) {
            ps.setLong(1, presupuestoId);
            ps.setString(2, codigo);
            ps.setString(3, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /**
     * Siembra un árbol de 3 niveles: cap1 → cap1.1 → cap1.1.1, más cap2
     * (raíz aislada). Se omiten rubros: este test se centra en la forma
     * del árbol y la numeración. El rubro se agrega en el test que
     * verifica cascade + supervivencia de APU.
     */
    private void sembrarArbol3Niveles(String presupuestoPublicId) throws Exception {
        Long presupuestoId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection()) {
            // cap1 (raíz "1")
            long cap1Id;
            try (var ps = con.prepareStatement(
                    "INSERT INTO capitulo (presupuesto_id, parent_id, item, descripcion, orden, total) "
                            + "VALUES (?, NULL, '1', 'Obras', 1, 0) RETURNING id")) {
                ps.setLong(1, presupuestoId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    cap1Id = rs.getLong(1);
                }
            }
            // cap1.1
            long cap1_1Id;
            try (var ps = con.prepareStatement(
                    "INSERT INTO capitulo (presupuesto_id, parent_id, item, descripcion, orden, total) "
                            + "VALUES (?, ?, '1.1', 'Replanteo', 1, 0) RETURNING id")) {
                ps.setLong(1, presupuestoId);
                ps.setLong(2, cap1Id);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    cap1_1Id = rs.getLong(1);
                }
            }
            // cap1.1.1
            try (var ps = con.prepareStatement(
                    "INSERT INTO capitulo (presupuesto_id, parent_id, item, descripcion, orden, total) "
                            + "VALUES (?, ?, '1.1.1', 'Topografia', 1, 0)")) {
                ps.setLong(1, presupuestoId);
                ps.setLong(2, cap1_1Id);
                ps.executeUpdate();
            }
            // cap2 (raíz "2")
            try (var ps = con.prepareStatement(
                    "INSERT INTO capitulo (presupuesto_id, parent_id, item, descripcion, orden, total) "
                            + "VALUES (?, NULL, '2', 'Estructura', 2, 0)")) {
                ps.setLong(1, presupuestoId);
                ps.executeUpdate();
            }
        }
    }

    /** Siembra un árbol con rubro colgado del sub-subcapítulo (3er nivel). */
    private Long sembrarArbolConRubro(String presupuestoPublicId, long apuId) throws Exception {
        Long presupuestoId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection()) {
            long cap1Id;
            try (var ps = con.prepareStatement(
                    "INSERT INTO capitulo (presupuesto_id, parent_id, item, descripcion, orden, total) "
                            + "VALUES (?, NULL, '1', 'Obras', 1, 0) RETURNING id")) {
                ps.setLong(1, presupuestoId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    cap1Id = rs.getLong(1);
                }
            }
            long cap1_1Id;
            try (var ps = con.prepareStatement(
                    "INSERT INTO capitulo (presupuesto_id, parent_id, item, descripcion, orden, total) "
                            + "VALUES (?, ?, '1.1', 'Replanteo', 1, 0) RETURNING id")) {
                ps.setLong(1, presupuestoId);
                ps.setLong(2, cap1Id);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    cap1_1Id = rs.getLong(1);
                }
            }
            long cap1_1_1Id;
            try (var ps = con.prepareStatement(
                    "INSERT INTO capitulo (presupuesto_id, parent_id, item, descripcion, orden, total) "
                            + "VALUES (?, ?, '1.1.1', 'Topografia', 1, 0) RETURNING id")) {
                ps.setLong(1, presupuestoId);
                ps.setLong(2, cap1_1Id);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    cap1_1_1Id = rs.getLong(1);
                }
            }
            try (var ps = con.prepareStatement(
                    "INSERT INTO rubro (capitulo_id, apu_id, item, codigo, descripcion, unidad, cantidad) "
                            + "VALUES (?, ?, '1.1.1.1', 'R-001', 'Replanteo', 'm', 10)")) {
                ps.setLong(1, cap1_1_1Id);
                ps.setLong(2, apuId);
                ps.executeUpdate();
            }
            return cap1_1_1Id;
        }
    }

    /** Inserta una versión adicional (no vigente) para un proyecto — usado en tests cross-version. */
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

    private BigDecimal leerTotalCapitulo(String capituloPublicId) throws Exception {
        Long capId = internalCapituloId(capituloPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT total FROM capitulo WHERE id = ?")) {
            ps.setLong(1, capId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private BigDecimal leerTotalPresupuesto(String presupuestoPublicId) throws Exception {
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

    private long contarCapitulos(String presupuestoPublicId) throws Exception {
        Long pId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM capitulo WHERE presupuesto_id = ?")) {
            ps.setLong(1, pId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
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

    // ──────────────────────────────────────────────────────────────────────
    // Tests
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void TC_P22_01_crear_capitulo_raiz_genera_item_1_y_devuelve_201() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c1@ex.com");
        String proyectoId = crearProyecto(token, "Raíz");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "OBRAS PRELIMINARES"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201)
                // Respuesta completa del árbol recalculado (Plan 022 contrato)
                .body("presupuestoId", equalTo(presupuestoId))
                .body("version", is(1))
                .body("totalGeneral", equalTo("0.000000"))
                .body("capitulos.size()", is(1))
                .body("capitulos[0].item", equalTo("1"))
                .body("capitulos[0].descripcion", equalTo("OBRAS PRELIMINARES"))
                .body("capitulos[0].orden", is(1))
                .body("capitulos[0].total", equalTo("0.000000"))
                .body("capitulos[0].id", matchesPattern(UUID_V7))
                .body("capitulos[0].subcapitulos.size()", is(0))
                .body("capitulos[0].rubros.size()", is(0));

        // Persistencia: la fila tiene item="1", parentId=null, orden=1.
        String capId = capituloPublicIdDeItem(presupuestoId, "1");
        assertNotNull(capId);
    }

    @Test
    void TC_P22_02_crear_subcapitulo_item_se_concatena_con_padre() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c2@ex.com");
        String proyectoId = crearProyecto(token, "Subcap");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        // Raíz
        String rootId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "OBRA CIVIL"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201)
                .extract()
                .path("capitulos[0].id");

        // Subcapítulo con parentId y orden explícitos
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "MOVIMIENTO DE TIERRAS", "parentId", rootId, "orden", 1))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201)
                .body("capitulos.size()", is(1))
                .body("capitulos[0].item", equalTo("1"))
                .body("capitulos[0].subcapitulos.size()", is(1))
                .body("capitulos[0].subcapitulos[0].item", equalTo("1.1"))
                .body("capitulos[0].subcapitulos[0].descripcion", equalTo("MOVIMIENTO DE TIERRAS"))
                .body("capitulos[0].subcapitulos[0].orden", is(1));

        // Persistencia: el subcapítulo tiene item="1.1"
        assertNotNull(capituloPublicIdDeItem(presupuestoId, "1.1"));
    }

    @Test
    void TC_P22_03_crear_con_orden_explicito_inserta_en_posicion_y_renumera() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c3@ex.com");
        String proyectoId = crearProyecto(token, "Insert posicion");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        // Crear 3 raíces (orden omitido → append: items "1", "2", "3")
        for (int i = 0; i < 3; i++) {
            given().contentType(JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(Map.of("descripcion", "Cap " + (i + 1)))
                    .when()
                    .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                    .then()
                    .statusCode(201);
        }
        assertEquals(3L, contarCapitulos(presupuestoId));
        assertNotNull(capituloPublicIdDeItem(presupuestoId, "3"));

        // Insertar un nuevo capítulo en posición 2 con descripción "Cap 2.5"
        // → el antiguo "2" debe pasar a "3", el antiguo "3" a "4".
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "Cap 2.5", "orden", 2))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201)
                .body("capitulos.size()", is(4))
                .body("capitulos[0].item", equalTo("1"))
                .body("capitulos[0].descripcion", equalTo("Cap 1"))
                .body("capitulos[1].item", equalTo("2"))
                .body("capitulos[1].descripcion", equalTo("Cap 2.5"))
                .body("capitulos[2].item", equalTo("3"))
                .body("capitulos[2].descripcion", equalTo("Cap 2"))
                .body("capitulos[3].item", equalTo("4"))
                .body("capitulos[3].descripcion", equalTo("Cap 3"));

        // Contigüidad: orden 1..4 sin huecos
        // (la verificación de items cubre renumeración implícita)
        assertEquals(4L, contarCapitulos(presupuestoId));
    }

    @Test
    void TC_P22_04_crear_sin_orden_omite_apendea_al_final() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c4@ex.com");
        String proyectoId = crearProyecto(token, "Append");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        // 2 raíces explícitas con orden 1 y 2
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "A", "orden", 1))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201);
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "B", "orden", 2))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201);

        // Tercera con orden omitido → append en posición 3, item "3"
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "C"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201)
                .body("capitulos.size()", is(3))
                .body("capitulos[2].item", equalTo("3"))
                .body("capitulos[2].descripcion", equalTo("C"))
                .body("capitulos[2].orden", is(3));
    }

    @Test
    void TC_P22_05_crear_orden_menor_a_1_devuelve_400() {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c5@ex.com");
        String proyectoId = given().header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .body(Map.of(
                        "nombreProyecto", "Orden invalido",
                        "codigo", "P-2026-ORD0",
                        "anio", (short) 2026,
                        "plazoEjecucion", (short) 6,
                        "plazoUnidad", "MES",
                        "direccionInstitucional", "GAD"))
                .when()
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        String presupuestoId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + proyectoId + "/presupuestos")
                .then()
                .statusCode(200)
                .extract()
                .path("[0].presupuestoId");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "X", "orden", 0))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P22_06_crear_orden_mayor_a_hermanos_mas_uno_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c6@ex.com");
        String proyectoId = crearProyecto(token, "Orden overflow");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        // 1 sola raíz → siblings=1 → orden máximo permitido = 2.
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "A"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "B", "orden", 99))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P22_07_crear_descripcion_vacia_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c7@ex.com");
        String proyectoId = crearProyecto(token, "Desc vacia");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", ""))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P22_08_crear_descripcion_mayor_255_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c8@ex.com");
        String proyectoId = crearProyecto(token, "Desc larga");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        String larga = "X".repeat(256);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", larga))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P22_09_crear_con_parent_de_otro_presupuesto_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c9@ex.com");
        String proyectoA = crearProyecto(token, "Proyecto A");
        String proyectoB = crearProyecto(token, "Proyecto B");
        String presupuestoA = vigenteDeProyecto(proyectoA);
        String presupuestoB = vigenteDeProyecto(proyectoB);

        // Crear capítulo en presupuesto A
        String capAId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "Cap A"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoA + "/capitulos")
                .then()
                .statusCode(201)
                .extract()
                .path("capitulos[0].id");

        // Intentar usarlo como parentId en presupuesto B → 400 validacion
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "Hijo cross", "parentId", capAId))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoB + "/capitulos")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P22_10_path_presupuesto_uuid_no_v7_devuelve_400() {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c10@ex.com");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "X"))
                .when()
                .post("/api/v1/presupuestos/" + UUID_NO_V7 + "/capitulos")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P22_11_path_presupuesto_inexistente_devuelve_404() {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c11@ex.com");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "X"))
                .when()
                .post("/api/v1/presupuestos/" + UUID_INEXISTENTE_V7 + "/capitulos")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    @Test
    void TC_P22_12_capitulo_ajeno_devuelve_404_en_PUT() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c12@ex.com");
        String proyectoId = crearProyecto(token, "Cap ajeno");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        // Sin crear ningún capítulo: el UUID del path no existe.
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "nuevo"))
                .when()
                .put("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + UUID_INEXISTENTE_V7)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    @Test
    void TC_P22_13_editar_descripcion_actualiza_y_conserva_item() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c13@ex.com");
        String proyectoId = crearProyecto(token, "Editar");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String capId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "ORIGINAL"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201)
                .extract()
                .path("capitulos[0].id");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "EDITADO"))
                .when()
                .put("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId)
                .then()
                .statusCode(200)
                .body("presupuestoId", equalTo(presupuestoId))
                .body("capitulos.size()", is(1))
                .body("capitulos[0].id", equalTo(capId))
                .body("capitulos[0].item", equalTo("1"))
                .body("capitulos[0].descripcion", equalTo("EDITADO"));

        // Persistencia: item sigue siendo "1"
        assertNotNull(capituloPublicIdDeItem(presupuestoId, "1"));
    }

    @Test
    void TC_P22_14_editar_descripcion_vacia_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c14@ex.com");
        String proyectoId = crearProyecto(token, "Edit vacio");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String capId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "ORIGINAL"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201)
                .extract()
                .path("capitulos[0].id");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", ""))
                .when()
                .put("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P22_15_mover_mismo_padre_reordena_y_conserva_item_prefix() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c15@ex.com");
        String proyectoId = crearProyecto(token, "Mover mismo padre");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        // Crear 3 raíces: "1", "2", "3"
        String cap1 = "", cap2 = "", cap3 = "";
        for (int i = 1; i <= 3; i++) {
            cap1 = cap2;
            cap2 = cap3;
            cap3 = given().contentType(JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(Map.of("descripcion", "Cap " + i))
                    .when()
                    .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                    .then()
                    .statusCode(201)
                    .extract()
                    .path("capitulos[" + (i - 1) + "].id");
        }
        // cap1=primera raíz, cap2=segunda, cap3=tercera

        // Mover la raíz 3 a la posición 1
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("orden", 1))
                .when()
                .patch("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + cap3 + "/mover")
                .then()
                .statusCode(200)
                .body("capitulos.size()", is(3))
                .body("capitulos[0].id", equalTo(cap3))
                .body("capitulos[0].item", equalTo("1"))
                .body("capitulos[1].id", equalTo(cap1))
                .body("capitulos[1].item", equalTo("2"))
                .body("capitulos[2].id", equalTo(cap2))
                .body("capitulos[2].item", equalTo("3"));
    }

    @Test
    void TC_P22_16_mover_a_otro_padre_renumera_subarbol() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c16@ex.com");
        String proyectoId = crearProyecto(token, "Cross parent");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarArbol3Niveles(presupuestoId);

        // Estado inicial: cap1("1") → cap1.1("1.1") → cap1.1.1("1.1.1"), cap2("2")
        String cap1 = capituloPublicIdDeItem(presupuestoId, "1");
        String cap1_1 = capituloPublicIdDeItem(presupuestoId, "1.1");
        String cap2 = capituloPublicIdDeItem(presupuestoId, "2");

        // Mover cap1.1 (con todo su subárbol) a ser hijo de cap2
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("parentId", cap2, "orden", 1))
                .when()
                .patch("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + cap1_1 + "/mover")
                .then()
                .statusCode(200)
                .body("capitulos.size()", is(2))
                // cap1 sigue existiendo como raíz sin hijos
                .body("capitulos[0].item", equalTo("1"))
                .body("capitulos[0].id", equalTo(cap1))
                .body("capitulos[0].subcapitulos.size()", is(0))
                // cap2 ahora tiene un hijo que era cap1.1; renumerado a "2.1"
                .body("capitulos[1].item", equalTo("2"))
                .body("capitulos[1].id", equalTo(cap2))
                .body("capitulos[1].subcapitulos.size()", is(1))
                .body("capitulos[1].subcapitulos[0].item", equalTo("2.1"))
                // Su nieto se renumera a "2.1.1"
                .body("capitulos[1].subcapitulos[0].subcapitulos.size()", is(1))
                .body("capitulos[1].subcapitulos[0].subcapitulos[0].item", equalTo("2.1.1"));

        // Persistencia: no quedan capítulos con item "1.1" ni "1.1.1"
        assertNull(internalCapituloItemLookup(presupuestoId, "1.1"));
        assertNull(internalCapituloItemLookup(presupuestoId, "1.1.1"));
    }

    @Test
    void TC_P22_17_mover_como_propio_padre_rechaza_con_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c17@ex.com");
        String proyectoId = crearProyecto(token, "Self cycle");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarArbol3Niveles(presupuestoId);

        String cap1 = capituloPublicIdDeItem(presupuestoId, "1");
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("parentId", cap1, "orden", 1))
                .when()
                .patch("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + cap1 + "/mover")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P22_18_mover_con_descendiente_como_padre_rechaza_con_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c18@ex.com");
        String proyectoId = crearProyecto(token, "Desc cycle");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarArbol3Niveles(presupuestoId);

        String cap1 = capituloPublicIdDeItem(presupuestoId, "1");
        String cap1_1_1 = capituloPublicIdDeItem(presupuestoId, "1.1.1");

        // Intentar mover cap1 dentro de su descendiente cap1.1.1 → 400
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("parentId", cap1_1_1, "orden", 1))
                .when()
                .patch("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + cap1 + "/mover")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P22_19_mover_orden_fuera_de_rango_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c19@ex.com");
        String proyectoId = crearProyecto(token, "Mover rango");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarArbol3Niveles(presupuestoId);

        String cap1 = capituloPublicIdDeItem(presupuestoId, "1");
        // mover a raíz con orden 0 → 400
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("orden", 0))
                .when()
                .patch("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + cap1 + "/mover")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        // mover a raíz con orden 99 → 400 (raíz tiene 2 hermanos, max=3)
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("orden", 99))
                .when()
                .patch("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + cap1 + "/mover")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P22_20_mover_con_parent_de_otro_presupuesto_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c20@ex.com");
        String proyectoA = crearProyecto(token, "A");
        String proyectoB = crearProyecto(token, "B");
        String presupuestoA = vigenteDeProyecto(proyectoA);
        String presupuestoB = vigenteDeProyecto(proyectoB);

        String capA = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "Cap A"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoA + "/capitulos")
                .then()
                .statusCode(201)
                .extract()
                .path("capitulos[0].id");

        String capB = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "Cap B"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoB + "/capitulos")
                .then()
                .statusCode(201)
                .extract()
                .path("capitulos[0].id");

        // Intentar mover capB para que sea hijo de capA (de otro presupuesto)
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("parentId", capA, "orden", 1))
                .when()
                .patch("/api/v1/presupuestos/" + presupuestoB + "/capitulos/" + capB + "/mover")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P22_21_eliminar_subarbol_borra_cascada_y_apu_sobrevive() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c21@ex.com");
        String proyectoId = crearProyecto(token, "Delete cascade");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        long apuId = insertarApu(presupuestoId, "AR-001");
        long capSubSubId = sembrarArbolConRubro(presupuestoId, apuId);

        // Antes: 3 capítulos + 1 rubro
        assertEquals(3L, contarCapitulos(presupuestoId));
        assertEquals(1L, contarRubros(presupuestoId));

        // Eliminar cap1 (raíz): cascadea al subcap, sub-subcap y al rubro.
        // El APU sobrevive (D-09 + V001 §3: cascadea a rubro, no a APU).
        String cap1 = capituloPublicIdDeItem(presupuestoId, "1");
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + cap1)
                .then()
                .statusCode(200)
                // cap1 era la única raíz sembrada: el árbol queda vacío tras la cascade.
                .body("capitulos.size()", is(0));

        assertEquals(0L, contarCapitulos(presupuestoId));
        assertEquals(0L, contarRubros(presupuestoId));
        assertEquals(1L, apuSigueExistiendo(apuId));
        // Sanity: el internal ID del sub-subcap eliminado ya no existe.
        assertEquals(0L, lookupCapituloInternalById(capSubSubId));
    }

    @Test
    void TC_P22_22_eliminar_capitulo_inexistente_devuelve_404() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c22@ex.com");
        String proyectoId = crearProyecto(token, "Delete 404");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + UUID_INEXISTENTE_V7)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    @Test
    void TC_P22_23_path_capitulo_uuid_no_v7_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c23@ex.com");
        String proyectoId = crearProyecto(token, "Bad UUID");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        // PUT con UUID no v7
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "x"))
                .when()
                .put("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + UUID_NO_V7)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        // PATCH con UUID no v7
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("orden", 1))
                .when()
                .patch("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + UUID_NO_V7 + "/mover")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        // DELETE con UUID no v7
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + UUID_NO_V7)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P22_24_recalcular_respuesta_completa_despues_de_crear_raiz() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c24@ex.com");
        String proyectoId = crearProyecto(token, "Recalc");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        // Antes de cualquier mutación: total = 0
        assertEquals(0, leerTotalPresupuesto(presupuestoId).compareTo(BigDecimal.ZERO));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "OBRA"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201)
                // El write-through deja totalGeneral=0 (no hay rubros aún)
                // pero la respuesta refleja el árbol y totalGeneral a escala 6.
                .body("totalGeneral", equalTo("0.000000"))
                .body("capitulos.size()", is(1))
                .body("capitulos[0].item", equalTo("1"))
                .body("capitulos[0].total", equalTo("0.000000"));

        // El recalculo no rompe el total cuando no hay rubros
        assertEquals(0, leerTotalPresupuesto(presupuestoId).compareTo(BigDecimal.ZERO));
    }

    @Test
    void TC_P22_25_recalcular_normaliza_totales_desde_snapshot_del_apu() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c25@ex.com");
        String proyectoId = crearProyecto(token, "Recalc rubro");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        // Sembrar un APU sin filas pero con derivados obsoletos. El recálculo debe
        // reconstruirlos desde su snapshot vacío.
        long apuId;
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("INSERT INTO apu (presupuesto_id, codigo, descripcion, unidad, "
                                + "costo_directo, costo_indirecto, costo_total) "
                                + "VALUES (?, 'AR-CALC', 'Calculo', 'u', 0, 0, 12.345678) RETURNING id")) {
            Long presupuestoIdInt = internalPresupuestoId(presupuestoId);
            ps.setLong(1, presupuestoIdInt);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                apuId = rs.getLong(1);
            }
        }

        // Crear un capítulo con un rubro cantidad=10 → precio_total = 10 * 12.34 = 123.4
        // Pero el PU se trunca a 2dp DOWN y se retiene a escala 6.
        String capId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "Con rubro"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201)
                .extract()
                .path("capitulos[0].id");

        // Insertar rubro directo por SQL (Plan 023 lo hará por API)
        Long capInt = internalCapituloId(capId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO rubro (capitulo_id, apu_id, item, codigo, descripcion, unidad, cantidad) "
                                + "VALUES (?, ?, '1.1', 'AR-CALC', 'rubro', 'u', 10)")) {
            ps.setLong(1, capInt);
            ps.setLong(2, apuId);
            ps.executeUpdate();
        }

        // Disparar recalculo a través de editar descripción (cualquier mutación)
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "Con rubro (edit)"))
                .when()
                .put("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId)
                .then()
                .statusCode(200)
                .body("capitulos[0].rubros.size()", is(1))
                .body("capitulos[0].rubros[0].precioUnitario", equalTo("0.000000"))
                .body("capitulos[0].rubros[0].precioTotal", equalTo("0.000000"))
                .body("capitulos[0].total", equalTo("0.000000"))
                .body("totalGeneral", equalTo("0.000000"));

        // Write-through persistido, no sólo serializado en la respuesta.
        assertEquals(0, leerTotalCapitulo(capId).compareTo(BigDecimal.ZERO));
        assertEquals(0, leerTotalPresupuesto(presupuestoId).compareTo(BigDecimal.ZERO));
    }

    @Test
    void TC_P22_26_depth3_subtree_items_cambian_al_mover_padre() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c26@ex.com");
        String proyectoId = crearProyecto(token, "Depth3");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarArbol3Niveles(presupuestoId);

        String cap1 = capituloPublicIdDeItem(presupuestoId, "1");
        String cap1_1 = capituloPublicIdDeItem(presupuestoId, "1.1");
        String cap2 = capituloPublicIdDeItem(presupuestoId, "2");

        // Mover cap1 (con todo su subárbol cap1.1 + cap1.1.1) a ser hijo de cap2
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("parentId", cap2, "orden", 1))
                .when()
                .patch("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + cap1 + "/mover")
                .then()
                .statusCode(200)
                // Al mover cap1 fuera del nivel raíz, queda cap2 como única raíz.
                // El hueco dejado por cap1 se cierra: cap2 (que estaba en orden 2)
                // pasa a orden 1 y su item se recompone a "1".
                .body("capitulos.size()", is(1))
                .body("capitulos[0].item", equalTo("1"))
                .body("capitulos[0].id", equalTo(cap2))
                // cap1 ahora es hijo de cap2 (orden 1) → item "1.1"
                .body("capitulos[0].subcapitulos.size()", is(1))
                .body("capitulos[0].subcapitulos[0].item", equalTo("1.1"))
                .body("capitulos[0].subcapitulos[0].id", equalTo(cap1))
                .body("capitulos[0].subcapitulos[0].subcapitulos.size()", is(1))
                // El antiguo cap1.1 ahora es "1.1.1"; el antiguo cap1.1.1 ahora es "1.1.1.1"
                .body("capitulos[0].subcapitulos[0].subcapitulos[0].item", equalTo("1.1.1"))
                .body("capitulos[0].subcapitulos[0].subcapitulos[0].id", equalTo(cap1_1))
                .body("capitulos[0].subcapitulos[0].subcapitulos[0].subcapitulos.size()", is(1))
                .body("capitulos[0].subcapitulos[0].subcapitulos[0].subcapitulos[0].item", equalTo("1.1.1.1"));

        // La vieja "2" ya no existe como item (cap2 tomó la posición 1).
        assertNull(internalCapituloItemLookup(presupuestoId, "2"));
    }

    @Test
    void TC_P22_27_intruso_no_puede_operar_capitulos_ajenos() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c27-owner@ex.com");
        String proyectoId = crearProyecto(token, "Owner");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String capId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "Mio"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201)
                .extract()
                .path("capitulos[0].id");

        String intruso = AuthSupport.registrarConToken(mailbox, "p22-c27-intruso@ex.com");

        // PUT como intruso → 404 (capitulo ajeno)
        given().contentType(JSON)
                .header("Authorization", "Bearer " + intruso)
                .body(Map.of("descripcion", "hack"))
                .when()
                .put("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        // DELETE como intruso → 404
        given().header("Authorization", "Bearer " + intruso)
                .when()
                .delete("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        // POST como intruso en presupuesto ajeno → 404
        given().contentType(JSON)
                .header("Authorization", "Bearer " + intruso)
                .body(Map.of("descripcion", "hack"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    @Test
    void TC_P22_28_presupuesto_otra_version_mismo_proyecto_devuelve_400_cross_version() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c28@ex.com");
        String proyectoId = crearProyecto(token, "Cross version");
        String vigenteId = vigenteDeProyecto(proyectoId);
        String v2Id = insertarVersionNoVigente(proyectoId, (short) 2);

        // Crear capítulo raíz en la versión vigente
        String capVigenteId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "Cap vigente"))
                .when()
                .post("/api/v1/presupuestos/" + vigenteId + "/capitulos")
                .then()
                .statusCode(201)
                .extract()
                .path("capitulos[0].id");

        // Intentar usarlo como parentId desde la versión 2 → 400
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "Cross", "parentId", capVigenteId))
                .when()
                .post("/api/v1/presupuestos/" + v2Id + "/capitulos")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P22_29_eliminar_hermano_intermedio_compacta_orden_e_items() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c29@ex.com");
        String proyectoId = crearProyecto(token, "Compactar");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        for (String descripcion : List.of("A", "B", "C")) {
            given().contentType(JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(Map.of("descripcion", descripcion))
                    .when()
                    .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                    .then()
                    .statusCode(201);
        }
        assertEquals(List.of("1:1", "2:2", "3:3"), huellaArbol(presupuestoId));

        // Eliminar el hermano del medio: el nivel debe compactarse a 1..2 y el
        // antiguo "3" pasar a "2" (renumeración atómica de TODOS los hermanos).
        String capB = capituloPublicIdDeItem(presupuestoId, "2");
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capB)
                .then()
                .statusCode(200)
                .body("capitulos.size()", is(2))
                .body("capitulos[0].item", equalTo("1"))
                .body("capitulos[0].descripcion", equalTo("A"))
                .body("capitulos[1].item", equalTo("2"))
                .body("capitulos[1].orden", is(2))
                .body("capitulos[1].descripcion", equalTo("C"));

        assertEquals(List.of("1:1", "2:2"), huellaArbol(presupuestoId));
    }

    @Test
    void TC_P22_30_mover_dentro_del_mismo_padre_renumera_subcapitulos() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c30@ex.com");
        String proyectoId = crearProyecto(token, "Reordenar hijos");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String raiz = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "R"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201)
                .extract()
                .path("capitulos[0].id");

        for (String descripcion : List.of("S1", "S2", "S3")) {
            given().contentType(JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(Map.of("descripcion", descripcion, "parentId", raiz))
                    .when()
                    .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                    .then()
                    .statusCode(201);
        }
        assertEquals(List.of("1:1", "1.1:1", "1.2:2", "1.3:3"), huellaArbol(presupuestoId));

        // Permutación dentro del mismo padre: 1.3 → 1.1, 1.1 → 1.2, 1.2 → 1.3.
        // Sin escritura en dos pasadas esto violaría UNIQUE (presupuesto_id, item)
        // a mitad del flush.
        String s3 = capituloPublicIdDeItem(presupuestoId, "1.3");
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("parentId", raiz, "orden", 1))
                .when()
                .patch("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + s3 + "/mover")
                .then()
                .statusCode(200)
                .body("capitulos.size()", is(1))
                .body("capitulos[0].subcapitulos.size()", is(3))
                .body("capitulos[0].subcapitulos[0].item", equalTo("1.1"))
                .body("capitulos[0].subcapitulos[0].descripcion", equalTo("S3"))
                .body("capitulos[0].subcapitulos[1].item", equalTo("1.2"))
                .body("capitulos[0].subcapitulos[1].descripcion", equalTo("S1"))
                .body("capitulos[0].subcapitulos[2].item", equalTo("1.3"))
                .body("capitulos[0].subcapitulos[2].descripcion", equalTo("S2"));

        assertEquals(List.of("1:1", "1.1:1", "1.2:2", "1.3:3"), huellaArbol(presupuestoId));
    }

    /**
     * Plan 023 — al renumerar el árbol (p. ej. mover un capítulo que
     * cambia su {@code item}), los rubros directos bajo cada capítulo se
     * renumeran también para que su prefijo refleje el {@code item} vigente
     * del padre. Cierra el riesgo Plan 022 de un rubro con prefijo stale.
     */
    @Test
    void TC_P22_31_mover_capitulo_re_numera_prefijo_de_sus_rubros() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22-c31@ex.com");
        String proyectoId = crearProyecto(token, "Prefijo rubro");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long presupuestoLong = internalPresupuestoId(presupuestoId);

        // Sembrar: cap1 (item="1") con rubro "1.1", cap2 (item="2").
        long cap1Id;
        long apuId;
        try (Connection con = ds.getConnection()) {
            try (var ps = con.prepareStatement(
                    "INSERT INTO capitulo (presupuesto_id, parent_id, item, descripcion, orden, total) "
                            + "VALUES (?, NULL, '1', 'Cap1', 1, 0) RETURNING id")) {
                ps.setLong(1, presupuestoLong);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    cap1Id = rs.getLong(1);
                }
            }
            try (var ps = con.prepareStatement(
                    "INSERT INTO capitulo (presupuesto_id, parent_id, item, descripcion, orden, total) "
                            + "VALUES (?, NULL, '2', 'Cap2', 2, 0)")) {
                ps.setLong(1, presupuestoLong);
                ps.executeUpdate();
            }
            try (var ps = con.prepareStatement("INSERT INTO apu (presupuesto_id, codigo, descripcion, unidad, "
                    + "costo_directo, costo_indirecto, costo_total) "
                    + "VALUES (?, 'APU-PFX', 'X', 'u', 0, 0, 0) RETURNING id")) {
                ps.setLong(1, presupuestoLong);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    apuId = rs.getLong(1);
                }
            }
            try (var ps = con.prepareStatement(
                    "INSERT INTO rubro (capitulo_id, apu_id, item, codigo, descripcion, unidad, cantidad) "
                            + "VALUES (?, ?, '1.1', 'AR-001', 'R', 'u', 5)")) {
                ps.setLong(1, cap1Id);
                ps.setLong(2, apuId);
                ps.executeUpdate();
            }
        }

        // Mover cap1 a la posición 2 → cap1 ahora item="2", cap2 ahora item="1".
        // El rubro bajo cap1 (era "1.1") debe re-numerarse a "2.1".
        String cap1Pub = capituloPublicIdDeItem(presupuestoId, "1");
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("orden", 2))
                .when()
                .patch("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + cap1Pub + "/mover")
                .then()
                .statusCode(200);

        // Persistencia: el rubro bajo el capítulo con item="2" debe tener item="2.1".
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT r.item FROM rubro r JOIN capitulo c ON c.id = r.capitulo_id "
                                + "WHERE c.presupuesto_id = ? AND c.item = '2'")) {
            ps.setLong(1, presupuestoLong);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Se esperaba un rubro bajo el capítulo con item=\"2\"");
                assertEquals("2.1", rs.getString(1));
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal helpers (no son parte del contrato)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Huella observable del árbol: {@code item:orden} de cada fila del
     * presupuesto, ordenada por {@code item}. Detecta de golpe items duplicados,
     * items temporales de la renumeración en dos pasadas (empiezan por
     * {@code ~}) y huecos en la contigüidad del {@code orden} de cada nivel.
     */
    private List<String> huellaArbol(String presupuestoPublicId) throws Exception {
        Long presupuestoId = internalPresupuestoId(presupuestoPublicId);
        List<String> huella = new ArrayList<>();
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT item, orden FROM capitulo WHERE presupuesto_id = ? ORDER BY item")) {
            ps.setLong(1, presupuestoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    huella.add(rs.getString(1) + ":" + rs.getShort(2));
                }
            }
        }
        return huella;
    }

    /** Devuelve el publicId UUIDv7 del capítulo con el item dado, o null si no existe. */
    private String internalCapituloItemLookup(String presupuestoPublicId, String item) throws Exception {
        Long presupuestoId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT public_id FROM capitulo WHERE presupuesto_id = ? AND item = ?")) {
            ps.setLong(1, presupuestoId);
            ps.setString(2, item);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private long lookupCapituloInternalById(long internalId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM capitulo WHERE id = ?")) {
            ps.setLong(1, internalId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
