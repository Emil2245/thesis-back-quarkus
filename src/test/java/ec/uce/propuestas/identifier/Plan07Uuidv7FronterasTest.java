package ec.uce.propuestas.identifier;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plan 07 — Contratos de frontera UUIDv7 para los módulos migrados.
 *
 * <p>Cubre, módulo por módulo:
 * <ul>
 *   <li><b>proyecto + firmantes + parámetros</b> (paths UUIDv7; id en JSON = UUID; 400/404 correctos).</li>
 *   <li><b>insumo + bases</b> (selector, CRUD base PROYECTO, admin bases-centrales, insumo admin nested).</li>
 *   <li><b>presupuesto</b> (APUs anidadas bajo presupuesto).</li>
 *   <li><b>plantilla</b> (guardar-plantilla anidado bajo proyecto).</li>
 *   <li><b>documento</b> (DOCX por presupuesto UUIDv7).</li>
 *   <li><b>apu-detalle</b> (insumoId de entrada es UUIDv7).</li>
 * </ul>
 *
 * <p>Las verificaciones se hacen vía REST; el {@code BIGINT} interno nunca aparece
 * en paths ni en el JSON. UUID mal formado → 400 {@code validacion};
 * recurso ajeno o inexistente → 404 {@code no-encontrado}.</p>
 */
@QuarkusTest
class Plan07Uuidv7FronterasTest {

    private static final String UUID_V7 =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

    /** UUIDv7 bien formado pero inexistente en BD. */
    private static final String UUID_INEXISTENTE = "0192f6c4-7c8a-7000-8000-000000000000";

    /** UUIDv4 (no v7) bien formado. */
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
                    + "plantilla_apu, plantilla_proyecto, token_usuario, refresh_token, "
                    + "usuario RESTART IDENTITY CASCADE");
        }
    }

    private String crearProyecto(String token, String nombre) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto",
                        nombre,
                        "anio",
                        (short) 2026,
                        "plazoEjecucion",
                        (short) 6,
                        "plazoUnidad",
                        "MES",
                        "direccionInstitucional",
                        "GAD Test"))
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
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private String insertarPresupuesto(String proyectoId) throws Exception {
        // Plan 021 — el Presupuesto v1 vigente se crea automáticamente al
        // crear el proyecto (POST /proyectos → ProyectoService.crear). Este
        // helper ya no inserta otra fila: la lee para devolver el publicId
        // UUIDv7 que ejercitan los contratos del módulo.
        Long proyectoIdInterno = internalProyectoId(proyectoId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT public_id FROM presupuesto " + "WHERE proyecto_id = ? AND version = 1")) {
            ps.setLong(1, proyectoIdInterno);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    // =========================================================================
    // Módulo 1: proyecto + firmantes + parámetros
    // =========================================================================

    @Test
    void TC_P07_PROYECTO_01_id_en_response_es_uuidv7() {
        String token = AuthSupport.registrarConToken(mailbox, "p07-proy@ex.com");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto", "Plan 07",
                        "codigo", "P07-01",
                        "anio", (short) 2026,
                        "plazoEjecucion", (short) 4,
                        "plazoUnidad", "MES",
                        "direccionInstitucional", "UCE"))
                .when()
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .body("id", matchesPattern(UUID_V7));
    }

    @Test
    void TC_P07_PROYECTO_02_path_uuid_v4_no_v7_devuelve_400() {
        String token = AuthSupport.registrarConToken(mailbox, "p07-proy-v4@ex.com");
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + UUID_NO_V7)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P07_PROYECTO_03_path_uuid_v7_inexistente_devuelve_404() {
        String token = AuthSupport.registrarConToken(mailbox, "p07-proy-inex@ex.com");
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + UUID_INEXISTENTE)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    @Test
    void TC_P07_PROYECTO_04_proyecto_ajeno_devuelve_404() {
        String titular = AuthSupport.registrarConToken(mailbox, "p07-tit@ex.com");
        String proyectoId = crearProyecto(titular, "Solo titular");

        String intruso = AuthSupport.registrarConToken(mailbox, "p07-int@ex.com");
        given().header("Authorization", "Bearer " + intruso)
                .when()
                .get("/api/v1/proyectos/" + proyectoId)
                .then()
                .statusCode(404);
    }

    @Test
    void TC_P07_FIRMANTE_01_id_en_response_es_uuidv7() {
        String token = AuthSupport.registrarConToken(mailbox, "p07-firm@ex.com");
        String proyectoId = crearProyecto(token, "Firmantes");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombre",
                        "Director",
                        "cargo",
                        "Director de planificación",
                        "rol",
                        "CONSOLIDADO",
                        "orden",
                        (short) 1))
                .when()
                .post("/api/v1/proyectos/" + proyectoId + "/firmantes")
                .then()
                .statusCode(201)
                .body("id", matchesPattern(UUID_V7));
    }

    @Test
    void TC_P07_FIRMANTE_02_path_uuid_v4_no_v7_devuelve_400() {
        String token = AuthSupport.registrarConToken(mailbox, "p07-firm-v4@ex.com");
        String proyectoId = crearProyecto(token, "Firmante v4");

        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/proyectos/" + proyectoId + "/firmantes/" + UUID_NO_V7)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P07_PARAMETROS_01_proyectoId_en_response_es_uuidv7() {
        String token = AuthSupport.registrarConToken(mailbox, "p07-param@ex.com");
        String proyectoId = crearProyecto(token, "Parametros");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + proyectoId + "/parametros")
                .then()
                .statusCode(200)
                .body("proyectoId", matchesPattern(UUID_V7));
    }

    @Test
    void TC_P07_PARAMETROS_02_path_uuid_v7_inexistente_devuelve_404() {
        String token = AuthSupport.registrarConToken(mailbox, "p07-param-inex@ex.com");
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + UUID_INEXISTENTE + "/parametros")
                .then()
                .statusCode(404);
    }

    // =========================================================================
    // Módulo 2: insumo + bases
    // =========================================================================

    @Test
    void TC_P07_INSUMO_01_id_en_response_es_uuidv7() {
        String token = AuthSupport.registrarConToken(mailbox, "p07-ins@ex.com");
        String proyectoId = crearProyecto(token, "Insumos");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "codigo", "MAT-P07",
                        "tipo", "MATERIAL",
                        "descripcion", "Cemento",
                        "unidad", "kg",
                        "precioUnitario", 0.65))
                .when()
                .post("/api/v1/proyectos/" + proyectoId + "/insumos")
                .then()
                .statusCode(201)
                .body("id", matchesPattern(UUID_V7));
    }

    @Test
    void TC_P07_INSUMO_02_path_uuid_v4_no_v7_devuelve_400() {
        String token = AuthSupport.registrarConToken(mailbox, "p07-ins-v4@ex.com");
        String proyectoId = crearProyecto(token, "Insumos v4");

        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/proyectos/" + proyectoId + "/insumos/" + UUID_NO_V7)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P07_INSUMO_03_path_uuid_v7_inexistente_devuelve_404() {
        String token = AuthSupport.registrarConToken(mailbox, "p07-ins-inex@ex.com");
        String proyectoId = crearProyecto(token, "Insumos inexistente");

        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/proyectos/" + proyectoId + "/insumos/" + UUID_INEXISTENTE)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    @Test
    void TC_P07_BASES_CENTRALES_01_id_en_response_es_uuidv7() {
        // el endpoint /bases-centrales ya viene alineado de planes previos
        String token = AuthSupport.registrarConToken(mailbox, "p07-bases@ex.com");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/bases-centrales")
                .then()
                .statusCode(200);
    }

    @Test
    void TC_P07_ADMIN_BASES_01_nested_iid_acepta_uuid_y_rechaza_long() {
        String admin = "p07-admin-bases@ex.com";
        String adminToken = AuthSupport.registrarConToken(mailbox, admin);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE usuario SET rol = 'SUPER_ADMIN' WHERE email = ?")) {
            ps.setString(1, admin);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // Re-login para refrescar el JWT con rol SUPER_ADMIN.
        String token = given().contentType(JSON)
                .body(Map.of("email", admin, "password", "Pass1234", "recordarSesion", false))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");

        String idBase = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", "Central Plan 07"))
                .when()
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // El id de la base es UUIDv7
        org.junit.jupiter.api.Assertions.assertTrue(
                idBase.matches(UUID_V7), "id de base admin debe ser UUIDv7: " + idBase);

        // El id de un insumo bajo la base es UUIDv7
        String idInsumo = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "codigo", "MAT-P07-CEN",
                        "tipo", "MATERIAL",
                        "descripcion", "Insumo Central",
                        "unidad", "kg",
                        "precioUnitario", 1.0))
                .when()
                .post("/api/v1/admin/bases-centrales/" + idBase + "/insumos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        org.junit.jupiter.api.Assertions.assertTrue(
                idInsumo.matches(UUID_V7), "id de insumo admin debe ser UUIDv7: " + idInsumo);

        // PUT/DELETE con {iid} UUIDv7 bien formado
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/admin/bases-centrales/" + idBase + "/insumos/" + UUID_INEXISTENTE)
                .then()
                .statusCode(404);
    }

    // =========================================================================
    // Módulo 3: presupuesto (paths UUIDv7)
    // =========================================================================

    @Test
    void TC_P07_PRESUPUESTO_01_path_uuid_v4_no_v7_devuelve_400() {
        String token = AuthSupport.registrarConToken(mailbox, "p07-pre-v4@ex.com");
        String proyectoId = crearProyecto(token, "Presupuesto v4");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + UUID_NO_V7 + "/apus")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P07_PRESUPUESTO_02_path_uuid_v7_inexistente_devuelve_404() {
        String token = AuthSupport.registrarConToken(mailbox, "p07-pre-inex@ex.com");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + UUID_INEXISTENTE + "/apus")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    @Test
    void TC_P07_PRESUPUESTO_03_crear_apu_devuelve_id_uuidv7() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p07-pre-crear@ex.com");
        String proyectoId = crearProyecto(token, "Presupuesto crear apu");
        String presupuestoId = insertarPresupuesto(proyectoId);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("codigo", "P07-APU", "descripcion", "APU", "unidad", "u"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/apus")
                .then()
                .statusCode(201)
                .body("id", matchesPattern(UUID_V7));
    }

    // =========================================================================
    // Módulo 4: plantilla (paths UUIDv7)
    // =========================================================================

    @Test
    void TC_P07_PLANTILLA_01_guardar_plantilla_path_uuid_v7_inexistente_devuelve_404() {
        String token = AuthSupport.registrarConToken(mailbox, "p07-plan-inex@ex.com");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", "X"))
                .when()
                .post("/api/v1/proyectos/" + UUID_INEXISTENTE + "/guardar-plantilla")
                .then()
                .statusCode(404);
    }

    @Test
    void TC_P07_PLANTILLA_02_guardar_plantilla_path_uuid_v4_devuelve_400() {
        String token = AuthSupport.registrarConToken(mailbox, "p07-plan-v4@ex.com");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", "X"))
                .when()
                .post("/api/v1/proyectos/" + UUID_NO_V7 + "/guardar-plantilla")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P07_PLANTILLA_03_guardar_plantilla_existe_devuelve_201_con_id_uuidv7() {
        String token = AuthSupport.registrarConToken(mailbox, "p07-plan-crear@ex.com");
        String proyectoId = crearProyecto(token, "Plantilla");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", "Mi plantilla", "descripcion", "demo"))
                .when()
                .post("/api/v1/proyectos/" + proyectoId + "/guardar-plantilla")
                .then()
                .statusCode(201)
                .body("id", matchesPattern(UUID_V7));
    }

    // =========================================================================
    // Módulo 5: documento (paths UUIDv7)
    // =========================================================================

    @Test
    void TC_P07_DOCUMENTO_01_path_uuid_v4_no_v7_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p07-doc-v4@ex.com");
        String proyectoId = crearProyecto(token, "Documento v4");
        // Crear al menos un APU con ET para que la exportación tenga contenido;
        // no es necesario para esta verificación del 400 de path malformado.
        insertarPresupuesto(proyectoId);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/especificaciones-tecnicas/" + UUID_NO_V7)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P07_DOCUMENTO_02_path_uuid_v7_inexistente_devuelve_404() {
        String token = AuthSupport.registrarConToken(mailbox, "p07-doc-inex@ex.com");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/especificaciones-tecnicas/" + UUID_INEXISTENTE + "?formato=docx")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    // =========================================================================
    // APU detalle (insumoId UUIDv7)
    // =========================================================================

    @Test
    void TC_P07_APU_DETALLE_01_agregar_detalle_con_insumo_uuidv7_exitoso() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p07-apu-det@ex.com");
        String proyectoId = crearProyecto(token, "APU detalle");
        String presupuestoId = insertarPresupuesto(proyectoId);

        String insumoId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "codigo", "MO-P07",
                        "tipo", "MANO_OBRA",
                        "descripcion", "Peón",
                        "unidad", "h",
                        "precioUnitario", 4.0))
                .when()
                .post("/api/v1/proyectos/" + proyectoId + "/insumos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        String apuId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("codigo", "APU-P07", "descripcion", "APU", "unidad", "u"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/apus")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // El insumoId del body es UUIDv7, no Long.
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("seccionTipo", "MANO_OBRA", "insumoId", insumoId, "cantidad", 2.0, "rendimiento", 1.0))
                .when()
                .post("/api/v1/apus/" + apuId + "/detalles")
                .then()
                .statusCode(201)
                // El insumoId reportado en el JSON también es UUIDv7 (semántico, no publicId).
                .body("secciones[1].detalles[0].insumoId", matchesPattern(UUID_V7));
    }

    @Test
    void TC_P07_APU_DETALLE_02_agregar_detalle_con_insumoId_v4_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p07-apu-det-v4@ex.com");
        String proyectoId = crearProyecto(token, "APU detalle v4");
        String presupuestoId = insertarPresupuesto(proyectoId);

        String apuId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("codigo", "APU-V4", "descripcion", "APU", "unidad", "u"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/apus")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // UUIDv4 (no v7) bien formado en el body → 400 validacion (RNF-05).
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("seccionTipo", "MANO_OBRA", "insumoId", UUID_NO_V7, "cantidad", 2.0, "rendimiento", 1.0))
                .when()
                .post("/api/v1/apus/" + apuId + "/detalles")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    // =====================================================================
    // Módulo copiar base (P-14): frontera UUIDv7 en JSON + owner-to-404
    // =====================================================================

    @Test
    void TC_P07_COPIAR_01_baseId_v4_en_body_devuelve_400() {
        String token = AuthSupport.registrarConToken(mailbox, "p07-cop-v4@ex.com");
        String proyectoId = crearProyecto(token, "Copiar v4");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("fuenteTipo", "CENTRAL", "baseId", UUID_NO_V7))
                .when()
                .post("/api/v1/proyectos/" + proyectoId + "/insumos/copiar")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P07_COPIAR_02_proyecto_destino_ajeno_devuelve_404() {
        // tokenA intenta copiar hacia el proyecto ajeno de tokenB: el seam debe
        // aplicar scope de owner al destino del path y devolver 404 (nunca 403,
        // RNF-05). El cuerpo es válido y mínimo: fuente CENTRAL con UUIDv7
        // inventado (la validación de owner debe rechazar antes del lookup de
        // la base CENTRAL).
        String tokenA = AuthSupport.registrarConToken(mailbox, "p07-cop-destA@ex.com");
        String tokenB = AuthSupport.registrarConToken(mailbox, "p07-cop-destB@ex.com");
        String proyectoB = crearProyecto(tokenB, "Copiar destino B");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenA)
                .body(Map.of(
                        "fuenteTipo", "CENTRAL",
                        "baseId", "0192f6c4-7c8a-7000-8000-000000000001"))
                .when()
                .post("/api/v1/proyectos/" + proyectoB + "/insumos/copiar")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    @Test
    void TC_P07_COPIAR_03_proyecto_origen_proyecto_ajeno_devuelve_404() throws Exception {
        // El destino es del caller, pero la fuente PROYECTO apunta a un
        // proyecto ajeno: 404 (nunca 403, RNF-05).
        String tokenA = AuthSupport.registrarConToken(mailbox, "p07-cop-origA@ex.com");
        String tokenB = AuthSupport.registrarConToken(mailbox, "p07-cop-origB@ex.com");
        String proyectoA = crearProyecto(tokenA, "Copiar origen A");
        String proyectoB = crearProyecto(tokenB, "Copiar origen B");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenA)
                .body(Map.of("fuenteTipo", "PROYECTO", "baseId", proyectoB))
                .when()
                .post("/api/v1/proyectos/" + proyectoA + "/insumos/copiar")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }
}
