package ec.uce.propuestas.plantilla.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * Plan 06 (P-46, N04 §A8) — Pruebas REST de los recursos de plantillas de
 * proyecto:
 * <ul>
 *   <li>{@code GET/DELETE /plantillas-proyecto}</li>
 *   <li>{@code POST /proyectos/{proyectoId}/guardar-plantilla}</li>
 *   <li>{@code POST /proyectos/desde-plantilla/{plantillaId}}</li>
 * </ul>
 *
 * <p>Patrón: {@code @QuarkusTest} + TRUNCATE en {@code @BeforeEach} + helpers
 * de {@link AuthSupport}.
 */
@QuarkusTest
class PlantillaProyectoResourceIT {

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
                    + "plantilla_proyecto, token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    // =========================================================================
    // Helpers de sembrado via REST
    // =========================================================================

    private String crearProyecto(String token, String nombre) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto",
                        nombre,
                        "anio",
                        (short) 2026,
                        "plazoEjecucion",
                        (short) 4,
                        "plazoUnidad",
                        "MES",
                        "direccionInstitucional",
                        "UCE"))
                .when()
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String insertarPresupuestoVigente(String proyectoId) throws Exception {
        // Plan 021 — el Presupuesto v1 vigente se crea automáticamente al
        // crear el proyecto (POST /proyectos → ProyectoService.crear). Este
        // helper ya no inserta otra fila: la lee para devolver el publicId
        // UUIDv7 que ejercitan los tests de plantilla de proyecto.
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT public_id FROM presupuesto " + "WHERE proyecto_id = ? AND version = 1")) {
            ps.setLong(1, internalProyectoId(proyectoId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    /** Resuelve el {@code BIGINT} interno del proyecto a partir de su {@code publicId} UUIDv7. */
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

    private UUID sembrarPlantillaProyecto(Long usuarioId, String nombre, String descripcion, String snapshot)
            throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO plantilla_proyecto (usuario_id, nombre, descripcion, snapshot_estructura) "
                                + "VALUES (?, ?, ?, ?::jsonb) RETURNING public_id")) {
            ps.setLong(1, usuarioId);
            ps.setString(2, nombre);
            if (descripcion == null) ps.setNull(3, java.sql.Types.VARCHAR);
            else ps.setString(3, descripcion);
            ps.setString(4, snapshot);
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

    // =========================================================================
    // GET /plantillas-proyecto — owner-scoped
    // =========================================================================

    @Test
    void TC_PPR_01_listar_devuelve_solo_propias() throws Exception {
        String tokenAlice = AuthSupport.registrarConToken(mailbox, "alice-listar@ex.com");
        String tokenBob = AuthSupport.registrarConToken(mailbox, "bob-listar@ex.com");
        long aliceId = usuarioIdPorEmail("alice-listar@ex.com");
        long bobId = usuarioIdPorEmail("bob-listar@ex.com");

        UUID alice1 = sembrarPlantillaProyecto(aliceId, "Plantilla A1", null, "{\"capitulos\":[]}");
        UUID alice2 = sembrarPlantillaProyecto(aliceId, "Plantilla A2", "desc", "{\"capitulos\":[]}");
        UUID bob1 = sembrarPlantillaProyecto(bobId, "Plantilla B1", null, "{\"capitulos\":[]}");

        List<String> aliceIds = given().header("Authorization", "Bearer " + tokenAlice)
                .when()
                .get("/api/v1/plantillas-proyecto")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("id");
        assertTrue(aliceIds.contains(alice1.toString()));
        assertTrue(aliceIds.contains(alice2.toString()));
        assertTrue(!aliceIds.contains(bob1.toString()), "Bob's plantilla not visible to Alice");

        List<String> bobIds = given().header("Authorization", "Bearer " + tokenBob)
                .when()
                .get("/api/v1/plantillas-proyecto")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("id");
        assertTrue(bobIds.contains(bob1.toString()));
        assertTrue(!bobIds.contains(alice1.toString()), "Alice's plantilla not visible to Bob");
    }

    @Test
    void TC_PPR_02_detalle_ajeno_devuelve_404() throws Exception {
        String tokenAlice = AuthSupport.registrarConToken(mailbox, "alice-det@ex.com");
        String tokenBob = AuthSupport.registrarConToken(mailbox, "bob-det@ex.com");
        long aliceId = usuarioIdPorEmail("alice-det@ex.com");
        UUID alicePlantilla = sembrarPlantillaProyecto(aliceId, "Privada", null, "{\"capitulos\":[]}");

        given().header("Authorization", "Bearer " + tokenBob)
                .when()
                .get("/api/v1/plantillas-proyecto/" + alicePlantilla)
                .then()
                .statusCode(404);
    }

    @Test
    void TC_PPR_03_id_mal_formado_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "alice-bad@ex.com");
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/plantillas-proyecto/550e8400-e29b-41d4-a716-446655440000")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    // =========================================================================
    // DELETE /plantillas-proyecto/{id} — owner-to-404
    // =========================================================================

    @Test
    void TC_PPR_04_eliminar_propia_204_y_conserva_proyectos_creados() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "alice-del@ex.com");
        String proyectoId = crearProyecto(token, "Para plantilla");
        insertarPresupuestoVigente(proyectoId);

        String plantillaId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", "Borrable", "descripcion", "desc"))
                .when()
                .post("/api/v1/proyectos/" + proyectoId + "/guardar-plantilla")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // Borrar la plantilla — el proyecto origen debe seguir existiendo.
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/plantillas-proyecto/" + plantillaId)
                .then()
                .statusCode(204);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + proyectoId)
                .then()
                .statusCode(200);
    }

    @Test
    void TC_PPR_05_eliminar_ajena_devuelve_404() throws Exception {
        String tokenAlice = AuthSupport.registrarConToken(mailbox, "alice-dele@ex.com");
        String tokenBob = AuthSupport.registrarConToken(mailbox, "bob-dele@ex.com");
        long aliceId = usuarioIdPorEmail("alice-dele@ex.com");
        UUID alicePlantilla = sembrarPlantillaProyecto(aliceId, "Privada", null, "{\"capitulos\":[]}");

        given().header("Authorization", "Bearer " + tokenBob)
                .when()
                .delete("/api/v1/plantillas-proyecto/" + alicePlantilla)
                .then()
                .statusCode(404);
    }

    // =========================================================================
    // POST /proyectos/{proyectoId}/guardar-plantilla — owner-to-404
    // =========================================================================

    @Test
    void TC_PPR_06_guardar_desde_proyecto_propio_devuelve_201_con_snapshot() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "alice-guardar@ex.com");
        String proyectoId = crearProyecto(token, "Obras");
        insertarPresupuestoVigente(proyectoId);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", "Plantilla Obras", "descripcion", "desc demo"))
                .when()
                .post("/api/v1/proyectos/" + proyectoId + "/guardar-plantilla")
                .then()
                .statusCode(201)
                .body("nombre", equalTo("Plantilla Obras"))
                .body("descripcion", equalTo("desc demo"))
                .body("snapshotEstructura", notNullValue())
                .body("fechaCreacion", notNullValue());
    }

    @Test
    void TC_PPR_07_guardar_desde_proyecto_ajeno_devuelve_404() throws Exception {
        String tokenAlice = AuthSupport.registrarConToken(mailbox, "alice-gda@ex.com");
        String tokenBob = AuthSupport.registrarConToken(mailbox, "bob-gda@ex.com");
        String proyectoAlice = crearProyecto(tokenAlice, "Alice");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenBob)
                .body(Map.of("nombre", "Hack"))
                .when()
                .post("/api/v1/proyectos/" + proyectoAlice + "/guardar-plantilla")
                .then()
                .statusCode(404);
    }

    @Test
    void TC_PPR_08_guardar_nombre_vacio_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "alice-vac@ex.com");
        String proyectoId = crearProyecto(token, "Vacio");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", "  "))
                .when()
                .post("/api/v1/proyectos/" + proyectoId + "/guardar-plantilla")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    // =========================================================================
    // POST /proyectos/desde-plantilla/{plantillaId}
    // =========================================================================

    @Test
    void TC_PPR_10_aplicar_crea_proyecto_201_sin_advertencias() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "alice-apl@ex.com");
        long aliceId = usuarioIdPorEmail("alice-apl@ex.com");
        UUID plantilla = sembrarPlantillaProyecto(aliceId, "Vacia", null, "{\"capitulos\":[]}");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", "Nuevo proyecto"))
                .when()
                .post("/api/v1/proyectos/desde-plantilla/" + plantilla)
                .then()
                .statusCode(201)
                .body("proyecto.nombreProyecto", equalTo("Nuevo proyecto"))
                .body("proyecto.estado", equalTo("BORRADOR"))
                .body("proyecto.id", notNullValue());
    }

    @Test
    void TC_PPR_11_aplicar_con_faltante_devuelve_200_con_advertencias() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "alice-warn@ex.com");
        long aliceId = usuarioIdPorEmail("alice-warn@ex.com");
        UUID plantilla = sembrarPlantillaProyecto(
                aliceId,
                "Con faltante",
                null,
                "{\"capitulos\":[{\"item\":\"1\",\"descripcion\":\"OBRAS\",\"orden\":1,\"parentItem\":null,"
                        + "\"hijos\":[],\"rubros\":[{"
                        + "\"item\":\"1.1\",\"codigo\":\"RP-001\",\"descripcion\":\"X\",\"unidad\":\"m2\","
                        + "\"apu\":{\"codigo\":\"RP-001\",\"descripcion\":\"X\",\"unidad\":\"m2\",\"filas\":["
                        + "{\"seccionTipo\":\"EQUIPO\",\"esHerramientaMenor\":true},"
                        + "{\"seccionTipo\":\"MANO_OBRA\",\"insumoCodigo\":\"MO-FALTA\","
                        + "\"cantidad\":\"0.5\",\"rendimiento\":\"0.1\"}"
                        + "]}}]}]}");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", "Con advertencia"))
                .when()
                .post("/api/v1/proyectos/desde-plantilla/" + plantilla)
                .then()
                .statusCode(200)
                .body("proyecto.nombreProyecto", equalTo("Con advertencia"))
                .body("advertencias.size()", is(1))
                .body("advertencias[0].insumoCodigo", equalTo("MO-FALTA"))
                .body("advertencias[0].motivo", equalTo("no-existe-en-base-proyecto"));
    }

    @Test
    void TC_PPR_12_aplicar_plantilla_ajena_devuelve_404() throws Exception {
        String tokenAlice = AuthSupport.registrarConToken(mailbox, "alice-pa3@ex.com");
        String tokenBob = AuthSupport.registrarConToken(mailbox, "bob-pa3@ex.com");
        long aliceId = usuarioIdPorEmail("alice-pa3@ex.com");
        UUID alicePlantilla = sembrarPlantillaProyecto(aliceId, "Solo Alice", null, "{\"capitulos\":[]}");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenBob)
                .body(Map.of("nombre", "Hack"))
                .when()
                .post("/api/v1/proyectos/desde-plantilla/" + alicePlantilla)
                .then()
                .statusCode(404);
    }

    @Test
    void TC_PPR_13_aplicar_id_mal_formado_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "alice-pbid@ex.com");
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", "X"))
                .when()
                .post("/api/v1/proyectos/desde-plantilla/550e8400-e29b-41d4-a716-446655440000")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_PPR_14_aplicar_nombre_vacio_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "alice-pnv@ex.com");
        long aliceId = usuarioIdPorEmail("alice-pnv@ex.com");
        UUID plantilla = sembrarPlantillaProyecto(aliceId, "X", null, "{\"capitulos\":[]}");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", "  "))
                .when()
                .post("/api/v1/proyectos/desde-plantilla/" + plantilla)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    // =========================================================================
    // POST /proyectos (blank) sigue creando un proyecto sin plantilla
    // =========================================================================

    @Test
    void TC_PPR_15_blank_post_proyectos_no_pasa_por_plantilla_y_responde_201() throws Exception {
        // El endpoint canónico POST /proyectos debe seguir creando un proyecto
        // completamente en blanco. NO se redirige a través de lógica de
        // plantilla.
        String token = AuthSupport.registrarConToken(mailbox, "alice-blank@ex.com");

        String id = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto", "Blank",
                        "anio", (short) 2026,
                        "plazoEjecucion", (short) 4,
                        "plazoUnidad", "MES",
                        "direccionInstitucional", "UCE"))
                .when()
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .body("nombreProyecto", equalTo("Blank"))
                .body("estado", equalTo("BORRADOR"))
                .extract()
                .path("id");

        assertNotNull(id);
        assertEquals(7, UUID.fromString(id).version());
    }
}
