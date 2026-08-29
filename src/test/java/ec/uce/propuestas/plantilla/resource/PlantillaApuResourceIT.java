package ec.uce.propuestas.plantilla.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plan 04 (P-26) — Pruebas REST de los recursos de plantillas APU
 * ({@code /plantillas-apu}, {@code /apus/{id}/guardar-plantilla},
 * {@code POST /presupuestos/{id}/apus?plantillaId=…}). Patrón: herencia de
 * {@link PlantillaApuResourceITBase} con siembra vía SQL y helpers de
 * {@link AuthSupport}.
 */
@QuarkusTest
class PlantillaApuResourceIT {

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
                    + "insumo, base_insumos, parametros_proyecto, firmante, proyecto, plantilla_apu, "
                    + "token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    private Long crearProyecto(String token) {
        return ((Number) given().contentType(JSON)
                        .header("Authorization", "Bearer " + token)
                        .body(Map.of(
                                "nombreProyecto", "Plantilla test",
                                "anio", (short) 2026,
                                "plazoEjecucion", (short) 4,
                                "plazoUnidad", "MES",
                                "direccionInstitucional", "UCE"))
                        .when()
                        .post("/api/v1/proyectos")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("id"))
                .longValue();
    }

    private Long crearInsumo(
            String token,
            Long proyectoId,
            String codigo,
            String tipo,
            String descripcion,
            String unidad,
            double precio) {
        return ((Number) given().contentType(JSON)
                        .header("Authorization", "Bearer " + token)
                        .body(Map.of(
                                "codigo", codigo,
                                "tipo", tipo,
                                "descripcion", descripcion,
                                "unidad", unidad,
                                "precioUnitario", precio))
                        .when()
                        .post("/api/v1/proyectos/" + proyectoId + "/insumos")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("id"))
                .longValue();
    }

    private Long insertarPresupuesto(Long proyectoId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO presupuesto (proyecto_id, version, es_vigente) VALUES (?, 1, TRUE) RETURNING id")) {
            ps.setLong(1, proyectoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private String crearApu(String token, Long presupuestoId, String codigo) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("codigo", codigo, "descripcion", "APU base", "unidad", "u"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/apus")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private UUID sembrarPlantillaSistema(String nombre) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO plantilla_apu (nombre, tipo, snapshot_secciones) "
                                + "VALUES (?, 'SISTEMA', '{\"secciones\":[]}') RETURNING public_id")) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return (UUID) rs.getObject(1);
            }
        }
    }

    private UUID sembrarPlantillaPersonal(Long usuarioId, String nombre, String snapshot) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO plantilla_apu (nombre, tipo, usuario_id, snapshot_secciones) "
                                + "VALUES (?, 'PERSONAL', ?, ?::jsonb) RETURNING public_id")) {
            ps.setString(1, nombre);
            ps.setLong(2, usuarioId);
            ps.setString(3, snapshot);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return (UUID) rs.getObject(1);
            }
        }
    }

    private long usuarioIdPorEmail(String email) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM usuario WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Test
    void TC_PR_01_listar_devuelve_sistema_y_propias() throws Exception {
        String tokenAlice = AuthSupport.registrarConToken(mailbox, "alice-r@ex.com");
        String tokenBob = AuthSupport.registrarConToken(mailbox, "bob-r@ex.com");
        long aliceId = usuarioIdPorEmail("alice-r@ex.com");
        long bobId = usuarioIdPorEmail("bob-r@ex.com");

        UUID sistema = sembrarPlantillaSistema("Catálogo sistema");
        UUID personalAlice = sembrarPlantillaPersonal(aliceId, "Alice obras", "{\"secciones\":[]}");
        UUID personalBob = sembrarPlantillaPersonal(bobId, "Bob obras", "{\"secciones\":[]}");

        // Alice ve SISTEMA + sus personales (3)
        List<String> aliceIds = given().header("Authorization", "Bearer " + tokenAlice)
                .when()
                .get("/api/v1/plantillas-apu")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("id");
        assertTrue(aliceIds.contains(sistema.toString()));
        assertTrue(aliceIds.contains(personalAlice.toString()));
        assertFalse(aliceIds.contains(personalBob.toString()), "Alice no debe ver la PERSONAL de Bob");

        // Bob ve SISTEMA + sus personales
        List<String> bobIds = given().header("Authorization", "Bearer " + tokenBob)
                .when()
                .get("/api/v1/plantillas-apu")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("id");
        assertTrue(bobIds.contains(sistema.toString()));
        assertFalse(bobIds.contains(personalAlice.toString()));
    }

    @Test
    void TC_PR_02_detalle_personal_ajena_devuelve_404() throws Exception {
        String tokenAlice = AuthSupport.registrarConToken(mailbox, "alice-det@ex.com");
        String tokenBob = AuthSupport.registrarConToken(mailbox, "bob-det@ex.com");
        long aliceId = usuarioIdPorEmail("alice-det@ex.com");
        UUID personalAlice = sembrarPlantillaPersonal(aliceId, "Privada Alice", "{\"secciones\":[]}");

        given().header("Authorization", "Bearer " + tokenBob)
                .when()
                .get("/api/v1/plantillas-apu/" + personalAlice)
                .then()
                .statusCode(404);
    }

    @Test
    void TC_PR_03_editar_sistema_devuelve_404() throws Exception {
        String tokenAlice = AuthSupport.registrarConToken(mailbox, "alice-sis@ex.com");
        UUID sistema = sembrarPlantillaSistema("Sistema");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenAlice)
                .body(Map.of("nombre", "Hack"))
                .when()
                .put("/api/v1/plantillas-apu/" + sistema)
                .then()
                .statusCode(404);
    }

    @Test
    void TC_PR_04_editar_personal_propia_200() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "alice-ed@ex.com");
        long aliceId = usuarioIdPorEmail("alice-ed@ex.com");
        UUID personal = sembrarPlantillaPersonal(aliceId, "Original", "{\"secciones\":[]}");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", "Renombrada", "descripcionRubro", "Desc nueva"))
                .when()
                .put("/api/v1/plantillas-apu/" + personal)
                .then()
                .statusCode(200)
                .body("nombre", equalTo("Renombrada"))
                .body("descripcionRubro", equalTo("Desc nueva"));
    }

    @Test
    void TC_PR_05_eliminar_personal_propia_204_y_no_altera_apus() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "alice-del@ex.com");
        long aliceId = usuarioIdPorEmail("alice-del@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        String apuId = crearApu(token, presupuestoId, "APU-PRE");

        // Crear plantilla PERSONAL desde el APU
        String plantillaId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", "Borrable", "descripcionRubro", "desc"))
                .when()
                .post("/api/v1/apus/" + apuId + "/guardar-plantilla")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // Eliminar la plantilla
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/plantillas-apu/" + plantillaId)
                .then()
                .statusCode(204);

        // El APU original sigue existiendo y consultable
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId)
                .then()
                .statusCode(200)
                .body("id", equalTo(apuId));
    }

    @Test
    void TC_PR_06_eliminar_personal_ajena_devuelve_404() throws Exception {
        String tokenAlice = AuthSupport.registrarConToken(mailbox, "alice-da@ex.com");
        String tokenBob = AuthSupport.registrarConToken(mailbox, "bob-da@ex.com");
        long aliceId = usuarioIdPorEmail("alice-da@ex.com");
        UUID personalAlice = sembrarPlantillaPersonal(aliceId, "Privada", "{\"secciones\":[]}");

        given().header("Authorization", "Bearer " + tokenBob)
                .when()
                .delete("/api/v1/plantillas-apu/" + personalAlice)
                .then()
                .statusCode(404);
    }

    @Test
    void TC_PR_07_guardar_desde_apu_propia_devuelve_201_con_snapshot() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "alice-g@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        String apuId = crearApu(token, presupuestoId, "APU-1");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", "Plantilla APU-1", "descripcionRubro", "demo"))
                .when()
                .post("/api/v1/apus/" + apuId + "/guardar-plantilla")
                .then()
                .statusCode(201)
                .body("nombre", equalTo("Plantilla APU-1"))
                .body("tipo", equalTo("PERSONAL"))
                .body("unidad", equalTo("u"));
    }

    @Test
    void TC_PR_08_guardar_desde_apu_ajena_devuelve_404() throws Exception {
        String tokenAlice = AuthSupport.registrarConToken(mailbox, "alice-ga@ex.com");
        String tokenBob = AuthSupport.registrarConToken(mailbox, "bob-ga@ex.com");
        Long proyectoAlice = crearProyecto(tokenAlice);
        Long presupuestoAlice = insertarPresupuesto(proyectoAlice);
        String apuAlice = crearApu(tokenAlice, presupuestoAlice, "APU-A");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenBob)
                .body(Map.of("nombre", "Plantilla hack"))
                .when()
                .post("/api/v1/apus/" + apuAlice + "/guardar-plantilla")
                .then()
                .statusCode(404);
    }

    @Test
    void TC_PR_10_crear_apu_desde_plantilla_sin_faltantes_devuelve_201() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "alice-load@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        Long insumoMo = crearInsumo(token, proyectoId, "MO-001", "MANO_OBRA", "Maestro", "h", 4.75);
        long aliceId = usuarioIdPorEmail("alice-load@ex.com");
        UUID plantilla = sembrarPlantillaPersonal(
                aliceId,
                "Plantilla completa",
                "{\"secciones\":["
                        + "{\"tipo\":\"EQUIPO\",\"lineas\":[{\"esHerramientaMenor\":true}]},"
                        + "{\"tipo\":\"MANO_OBRA\",\"lineas\":[{\"insumoCodigo\":\"MO-001\",\"cantidad\":\"1\",\"rendimiento\":\"1\"}]},"
                        + "{\"tipo\":\"MATERIAL\",\"lineas\":[]},"
                        + "{\"tipo\":\"TRANSPORTE\",\"lineas\":[]}]}");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "codigo", "APU-LOAD",
                        "descripcion", "Desde plantilla",
                        "unidad", "m",
                        "plantillaId", plantilla.toString()))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/apus")
                .then()
                .statusCode(201)
                .body("codigo", equalTo("APU-LOAD"))
                .body("secciones[0].detalles[0].esHerramientaMenor", is(true))
                .body("secciones[1].detalles[0].descripcion", equalTo("Maestro"));
    }

    @Test
    void TC_PR_11_crear_apu_desde_plantilla_con_faltantes_devuelve_200_con_advertencias() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "alice-warn@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        long aliceId = usuarioIdPorEmail("alice-warn@ex.com");
        UUID plantilla = sembrarPlantillaPersonal(
                aliceId,
                "Plantilla con faltante",
                "{\"secciones\":["
                        + "{\"tipo\":\"EQUIPO\",\"lineas\":[{\"esHerramientaMenor\":true}]},"
                        + "{\"tipo\":\"MANO_OBRA\",\"lineas\":[{\"insumoCodigo\":\"MO-FALTA\",\"cantidad\":\"1\",\"rendimiento\":\"1\"}]},"
                        + "{\"tipo\":\"MATERIAL\",\"lineas\":[]},"
                        + "{\"tipo\":\"TRANSPORTE\",\"lineas\":[]}]}");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "codigo", "APU-WARN",
                        "descripcion", "Con advertencia",
                        "unidad", "m",
                        "plantillaId", plantilla.toString()))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/apus")
                .then()
                .statusCode(200)
                .body("codigo", equalTo("APU-WARN"))
                .body("advertencias.size()", is(1))
                .body("advertencias[0].insumoCodigo", equalTo("MO-FALTA"))
                .body("advertencias[0].motivo", equalTo("no-existe-en-base-proyecto"));
    }

    @Test
    void TC_PR_12_crear_apu_plantilla_ajena_devuelve_404() throws Exception {
        String tokenAlice = AuthSupport.registrarConToken(mailbox, "alice-pa@ex.com");
        String tokenBob = AuthSupport.registrarConToken(mailbox, "bob-pa@ex.com");
        Long proyectoBob = crearProyecto(tokenBob);
        Long presupuestoBob = insertarPresupuesto(proyectoBob);
        long aliceId = usuarioIdPorEmail("alice-pa@ex.com");
        UUID personalAlice = sembrarPlantillaPersonal(aliceId, "Solo Alice", "{\"secciones\":[]}");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenBob)
                .body(Map.of(
                        "codigo", "APU-X",
                        "descripcion", "X",
                        "unidad", "m",
                        "plantillaId", personalAlice.toString()))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoBob + "/apus")
                .then()
                .statusCode(404);
    }

    @Test
    void TC_PR_13_crear_apu_plantilla_id_mal_formado_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "alice-bad@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "codigo", "APU-BAD",
                        "descripcion", "Bad",
                        "unidad", "m",
                        "plantillaId", "550e8400-e29b-41d4-a716-446655440000"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/apus")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }
}