package ec.uce.propuestas.presupuesto.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plan 021 — flujo REST de lectura del agregado {@code Presupuesto →
 * Capitulo → Rubro} (P-28/P-29/P-30 read model + P-31 listado de versiones).
 *
 * <p>Cubre: listado de versiones con orden descendente y owner-scope, read
 * model vacío (presupuesto recién creado sin árbol), read model poblado
 * (capítulos raíz + subcapítulos + rubros), frontera UUIDv7 → 400,
 * foreign → 404, e invariante «exactamente una vigente por proyecto»
 * (índice parcial V001 §2.8).</p>
 */
@QuarkusTest
class PresupuestoResourceIT {

    private static final String UUID_V7 =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

    /** UUIDv7 inexistente pero bien formado — usado para verificar 404 de la capa de owner. */
    private static final String UUID_INEXISTENTE_V7 = "0192f6c4-7c8a-7000-8000-000000000000";

    /** UUIDv4 (no v7) bien formado — usado para verificar la frontera de validación 400. */
    private static final String UUID_NO_V7 = "550e8400-e29b-41d4-a716-446655440000";

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

    private String crearProyecto(String token, String nombre) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto",
                        nombre,
                        "codigo",
                        "P-2026-P21",
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

    /** BIGINT interno del proyecto a partir de su {@code publicId} UUIDv7. */
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

    /** BIGINT interno del presupuesto a partir de su {@code publicId} UUIDv7. */
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

    /** Versión vigente UUIDv7 del proyecto (debe existir tras Plan 021 auto-create). */
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

    /** Inserta una versión adicional (no vigente) para un proyecto — usado en tests de listado. */
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

    /** BIGINT interno del APU a partir de su {@code publicId} UUIDv7 (sólo para sembrar rubros). */
    private Long internalApuId(String publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM apu WHERE public_id = ?")) {
            ps.setObject(1, java.util.UUID.fromString(publicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Crea un APU mínimo en un presupuesto (FK interno). */
    private String crearApu(String token, String presupuestoId, String codigo) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("codigo", codigo, "descripcion", "Item demo", "unidad", "u"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/apus")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    /**
     * Siembra un árbol: 2 capítulos raíz; el primero recursa a 3 niveles
     * (cap → subcap → sub-subcap → rubro) — la recursión del read model queda
     * ejercitada con un nido real, no con un único hijo. El segundo raíz
     * queda vacío para preservar el {@code size()=2} esperado por las
     * aserciones. Se omite un segundo rubro porque
     * {@code rubro.apu_id} es UNIQUE y no se le puede reasignar el mismo
     * APU al cap2 (sería necesario un APU distinto — se mantiene el test
     * conciso).
     */
    private void sembrarArbolDemo(String proyectoPublicId, String apuPublicId) throws Exception {
        Long proyectoId = internalProyectoId(proyectoPublicId);
        Long presupuestoId = internalPresupuestoId(vigenteDeProyecto(proyectoPublicId));
        Long apuId = internalApuId(apuPublicId);
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            // cap1 (raíz "1")
            st.executeUpdate(
                    "INSERT INTO capitulo (presupuesto_id, parent_id, item, descripcion, orden, total) " + "VALUES ("
                            + presupuestoId + ", NULL, '1', 'Obras preliminares', 1, 0)",
                    Statement.RETURN_GENERATED_KEYS);
            long cap1Id;
            try (ResultSet rs = st.getGeneratedKeys()) {
                rs.next();
                cap1Id = rs.getLong(1);
            }
            // cap1.1 (hijo de cap1)
            st.executeUpdate(
                    "INSERT INTO capitulo (presupuesto_id, parent_id, item, descripcion, orden, total) " + "VALUES ("
                            + presupuestoId + ", " + cap1Id + ", '1.1', 'Replanteo', 1, 0)",
                    Statement.RETURN_GENERATED_KEYS);
            long subcapId;
            try (ResultSet rs = st.getGeneratedKeys()) {
                rs.next();
                subcapId = rs.getLong(1);
            }
            // cap1.1.1 (nieto de cap1 — tercer nivel, garantiza recursión real)
            st.executeUpdate(
                    "INSERT INTO capitulo (presupuesto_id, parent_id, item, descripcion, orden, total) " + "VALUES ("
                            + presupuestoId + ", " + subcapId + ", '1.1.1', 'Topografia', 1, 0)",
                    Statement.RETURN_GENERATED_KEYS);
            long subsubcapId;
            try (ResultSet rs = st.getGeneratedKeys()) {
                rs.next();
                subsubcapId = rs.getLong(1);
            }
            // R-001 cuelga del sub-subcapítulo (3er nivel), no del cap raíz.
            st.executeUpdate("INSERT INTO rubro (capitulo_id, apu_id, item, codigo, descripcion, unidad, cantidad) "
                    + "VALUES (" + subsubcapId + ", " + apuId + ", '1.1.1.1', 'R-001', 'Replanteo manual', 'm', 10)");
            // cap2 (raíz "2"), sin hijos ni rubros — preserva size()=2.
            st.executeUpdate("INSERT INTO capitulo (presupuesto_id, parent_id, item, descripcion, orden, total) "
                    + "VALUES (" + presupuestoId + ", NULL, '2', 'Estructura', 2, 0)");
            // proyectoId sólo se valida para detectar FK rota.
            if (proyectoId == null) {
                throw new IllegalStateException("proyectoId nulo");
            }
        }
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    void TC_P21_01_listar_versiones_orden_desc_y_owner_scope() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p21-owner@ex.com");
        String proyectoId = crearProyecto(token, "Puente P21");

        // El auto-create deja 1 vigente. Insertamos una v2 no vigente para verificar el orden.
        String v2PublicId = insertarVersionNoVigente(proyectoId, (short) 2);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + proyectoId + "/presupuestos")
                .then()
                .statusCode(200)
                .body("size()", is(2))
                // v2 primero (orden DESC), luego v1
                .body("[0].version", is(2))
                .body("[0].esVigente", is(false))
                .body("[1].version", is(1))
                .body("[1].esVigente", is(true))
                .body("[0].presupuestoId", equalTo(v2PublicId))
                .body("[0].presupuestoId", matchesPattern(UUID_V7))
                // totalGeneral como cadena decimal a escala 6 (Plan 021 contrato)
                .body("[0].totalGeneral", equalTo("0.000000"))
                .body("[0].totalGeneral", notNullValue());

        // Owner-scope: un intruso pide el listado del mismo proyecto -> 404
        String intruso = AuthSupport.registrarConToken(mailbox, "p21-intruso@ex.com");
        given().header("Authorization", "Bearer " + intruso)
                .when()
                .get("/api/v1/proyectos/" + proyectoId + "/presupuestos")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    @Test
    void TC_P21_02_obtener_arbol_vacio_cuando_sin_capitulos() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p21-empty@ex.com");
        String proyectoId = crearProyecto(token, "Sin arbol");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId)
                .then()
                .statusCode(200)
                .body("presupuestoId", equalTo(presupuestoId))
                .body("presupuestoId", matchesPattern(UUID_V7))
                .body("version", is(1))
                .body("esVigente", is(true))
                .body("totalGeneral", equalTo("0.000000"))
                .body("capitulos.size()", is(0));
    }

    @Test
    void TC_P21_03_obtener_arbol_poblado_recursivo_con_rubros_y_apu_uuid() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p21-tree@ex.com");
        String proyectoId = crearProyecto(token, "Con arbol");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        // Crear APU mediante el endpoint existente (Plan 019) para tener un publicId UUIDv7.
        String apuId = crearApu(token, presupuestoId, "AR-001");
        sembrarArbolDemo(proyectoId, apuId);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId)
                .then()
                .statusCode(200)
                // Cabecera
                .body("presupuestoId", equalTo(presupuestoId))
                .body("version", is(1))
                .body("esVigente", is(true))
                .body("totalGeneral", equalTo("0.000000"))
                // Dos capítulos raíz ordenados por item ascendente ("1" antes que "2")
                .body("capitulos.size()", is(2))
                .body("capitulos[0].item", equalTo("1"))
                // cap → subcap → sub-subcap → rubro (3 niveles, recursión real)
                .body("capitulos[0].subcapitulos.size()", is(1))
                .body("capitulos[0].subcapitulos[0].item", equalTo("1.1"))
                .body("capitulos[0].subcapitulos[0].subcapitulos.size()", is(1))
                .body("capitulos[0].subcapitulos[0].subcapitulos[0].item", equalTo("1.1.1"))
                .body("capitulos[0].subcapitulos[0].subcapitulos[0].rubros.size()", is(1))
                .body("capitulos[0].subcapitulos[0].subcapitulos[0].rubros[0].codigo", equalTo("R-001"))
                // El rubro expone el apuId como UUID publico (Plan 07 / WU-03).
                .body("capitulos[0].subcapitulos[0].subcapitulos[0].rubros[0].apuId", equalTo(apuId))
                .body("capitulos[0].subcapitulos[0].subcapitulos[0].rubros[0].apuId", matchesPattern(UUID_V7))
                // Cantidad como cadena a escala 6 contractual (P-30).
                .body("capitulos[0].subcapitulos[0].subcapitulos[0].rubros[0].cantidad", equalTo("10.000000"))
                // Capítulo 2 raíz, sin subcapítulos ni rubros.
                .body("capitulos[1].item", equalTo("2"))
                .body("capitulos[1].subcapitulos.size()", is(0))
                .body("capitulos[1].rubros.size()", is(0))
                // Decimales como cadena (escala 6 contract)
                .body("capitulos[0].total", equalTo("0.000000"))
                .body("capitulos[0].id", matchesPattern(UUID_V7));
    }

    @Test
    void TC_P21_04_path_uuid_v4_no_v7_devuelve_400_validacion() {
        String token = AuthSupport.registrarConToken(mailbox, "p21-v4@ex.com");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + UUID_NO_V7)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + UUID_NO_V7 + "/presupuestos")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P21_05_path_uuid_malformado_devuelve_400_validacion() {
        String token = AuthSupport.registrarConToken(mailbox, "p21-mal@ex.com");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/no-es-uuid")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/esto-no-es-un-uuid/presupuestos")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P21_06_uuid_v7_ajeno_devuelve_404_no_encontrado() {
        String token = AuthSupport.registrarConToken(mailbox, "p21-fk@ex.com");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + UUID_INEXISTENTE_V7)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + UUID_INEXISTENTE_V7 + "/presupuestos")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    @Test
    void TC_P21_07_invariante_unica_vigente_por_proyecto() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p21-inv@ex.com");
        String proyectoId = crearProyecto(token, "Unico vigente");

        // Insertar una segunda fila con es_vigente=TRUE -> debe violar el indice
        // unico parcial ux_presupuesto_vigente (V001 §2.8) y por lo tanto fallar.
        Long proyectoInternoId = internalProyectoId(proyectoId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO presupuesto (proyecto_id, version, es_vigente) VALUES (?, 2, TRUE)")) {
            ps.setLong(1, proyectoInternoId);
            ps.executeUpdate();
            org.junit.jupiter.api.Assertions.fail("Se esperaba SQLException al insertar una segunda vigente");
        } catch (java.sql.SQLException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            String causeMsg = e.getCause() == null ? "" : e.getCause().getMessage();
            boolean mentionsIndex = msg.contains("ux_presupuesto_vigente")
                    || causeMsg.contains("ux_presupuesto_vigente")
                    || msg.toLowerCase().contains("duplicate")
                    || causeMsg.toLowerCase().contains("duplicate")
                    || msg.toLowerCase().contains("unique")
                    || causeMsg.toLowerCase().contains("unique");
            org.junit.jupiter.api.Assertions.assertTrue(
                    mentionsIndex,
                    "La SQLException debe provenir del indice unico parcial ux_presupuesto_vigente: " + msg
                            + " | cause=" + causeMsg);
        }
    }

    @Test
    void TC_P21_08_origen_id_y_uuid_publico_en_listado_de_versiones() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p21-origen@ex.com");
        String proyectoId = crearProyecto(token, "Origen");
        // El auto-create deja la v1 vigente con origen_id NULL -> campo origenId ausente
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + proyectoId + "/presupuestos")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].version", is(1))
                .body("[0].esVigente", is(true))
                .body("[0].origenId", nullValue())
                .body("[0].fechaCreacion", notNullValue());

        // Insertar una v2 con origen_id = v1 -> el listado debe inyectar el UUID publico del origen.
        String v1PublicId = vigenteDeProyecto(proyectoId);
        Long v1InternoId = internalPresupuestoId(v1PublicId);
        Long proyectoInternoId = internalProyectoId(proyectoId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO presupuesto (proyecto_id, version, es_vigente, origen_id, notas) "
                                + "VALUES (?, 2, FALSE, ?, 'Clon demo')")) {
            ps.setLong(1, proyectoInternoId);
            ps.setLong(2, v1InternoId);
            ps.executeUpdate();
        }
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + proyectoId + "/presupuestos")
                .then()
                .statusCode(200)
                .body("size()", is(2))
                .body("[0].version", is(2))
                .body("[0].origenId", equalTo(v1PublicId))
                .body("[0].origenId", matchesPattern(UUID_V7))
                .body("[0].notas", equalTo("Clon demo"));
    }

    @Test
    void TC_P21_09_arbol_no_filtra_rubros_de_otro_proyecto() throws Exception {
        // Verifica que el listado de rubros de un presupuesto solo trae los suyos,
        // no los de otro presupuesto de otro proyecto del mismo caller.
        String token = AuthSupport.registrarConToken(mailbox, "p21-iso@ex.com");
        String proyectoA = crearProyecto(token, "Aislar A");
        String proyectoB = crearProyecto(token, "Aislar B");
        String presupuestoA = vigenteDeProyecto(proyectoA);
        String presupuestoB = vigenteDeProyecto(proyectoB);
        String apuA = crearApu(token, presupuestoA, "AR-A");
        String apuB = crearApu(token, presupuestoB, "AR-B");
        sembrarArbolDemo(proyectoA, apuA);
        sembrarArbolDemo(proyectoB, apuB);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoA)
                .then()
                .statusCode(200)
                .body("capitulos.size()", is(2))
                // El rubro del proyecto A expone apuA, no apuB (path de 3 niveles).
                .body("capitulos[0].subcapitulos[0].subcapitulos[0].rubros[0].apuId", equalTo(apuA));
    }
}
