package ec.uce.propuestas.insumo.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BasesPersonalesResourceIT {

    private static final String UUID_V7 =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

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

    @Test
    void TC_BP_REST_01_crea_lista_y_aisla_por_propietario() {
        String tokenAlice = AuthSupport.registrarConToken(mailbox, "alice-personal@ex.com");
        String tokenBob = AuthSupport.registrarConToken(mailbox, "bob-personal@ex.com");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenAlice)
                .body(Map.of("nombre", "Mis insumos"))
                .when()
                .post("/api/v1/bases-personales")
                .then()
                .statusCode(201)
                .body("id", matchesPattern(UUID_V7))
                .body("nombre", equalTo("Mis insumos"))
                .body("archivada", equalTo(false))
                .body("totalInsumos", equalTo(0));

        given().header("Authorization", "Bearer " + tokenAlice)
                .when()
                .get("/api/v1/bases-personales")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].nombre", equalTo("Mis insumos"));

        given().header("Authorization", "Bearer " + tokenBob)
                .when()
                .get("/api/v1/bases-personales")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    void TC_BP_REST_02_nombre_blanco_devuelve_validacion() {
        String token = AuthSupport.registrarConToken(mailbox, "blank-personal@ex.com");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", "   "))
                .when()
                .post("/api/v1/bases-personales")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_BP_REST_03_requiere_autenticacion() {
        given().when().get("/api/v1/bases-personales").then().statusCode(401);
    }

    // ========================================================================
    // Plan 05 — Borrado físico con owner-to-404 + cascade FK
    // ========================================================================

    @Test
    void TC_BP_REST_04_duenno_puede_borrar_su_base_y_devuelve_204() {
        String token = AuthSupport.registrarConToken(mailbox, "owner-borra@ex.com");

        String id = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", "Para borrar"))
                .when()
                .post("/api/v1/bases-personales")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .extract()
                .path("id");

        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/bases-personales/" + id)
                .then()
                .statusCode(204);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/bases-personales")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    void TC_BP_REST_05_duenno_ajeno_intenta_borrar_y_devuelve_404() {
        String tokenAlice = AuthSupport.registrarConToken(mailbox, "alice-borra@ex.com");
        String tokenBob = AuthSupport.registrarConToken(mailbox, "bob-borra@ex.com");

        String id = given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenAlice)
                .body(Map.of("nombre", "Solo Alice"))
                .when()
                .post("/api/v1/bases-personales")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given().header("Authorization", "Bearer " + tokenBob)
                .when()
                .delete("/api/v1/bases-personales/" + id)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        given().header("Authorization", "Bearer " + tokenAlice)
                .when()
                .get("/api/v1/bases-personales")
                .then()
                .statusCode(200)
                .body("$", hasSize(1));
    }

    @Test
    void TC_BP_REST_06_borrado_elimina_insumos_asociados_por_cascade_FK() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "cascade-borra@ex.com");

        String idBase = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", "Para cascade"))
                .when()
                .post("/api/v1/bases-personales")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // Insertar un insumo asociado directamente via SQL para reproducir el
        // escenario de cascade (las bases PERSONALES no exponen CRUD de insumo
        // vía REST: la alimentación real es via "copia al usar" desde APUs).
        long baseIdInterno;
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO insumo (base_id, codigo, tipo, descripcion, unidad, precio_unitario) "
                                + "VALUES ((SELECT id FROM base_insumos WHERE public_id = ?::uuid), "
                                + "'MAT-CAS', 'MATERIAL', 'cascade', 'kg', 1.25)");
                PreparedStatement ps2 = con.prepareStatement("SELECT id FROM base_insumos WHERE public_id = ?::uuid")) {
            ps.setObject(1, UUID.fromString(idBase));
            ps.executeUpdate();
            ps2.setObject(1, UUID.fromString(idBase));
            try (ResultSet rs = ps2.executeQuery()) {
                rs.next();
                baseIdInterno = rs.getLong(1);
            }
        }

        long insumosAntes = contarInsumosDeBase(baseIdInterno);
        org.junit.jupiter.api.Assertions.assertEquals(1L, insumosAntes, "Insumo preexistente");

        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/bases-personales/" + idBase)
                .then()
                .statusCode(204);

        long insumosDespues = contarInsumosDeBase(baseIdInterno);
        long basesDespues = contarBases();
        org.junit.jupiter.api.Assertions.assertEquals(0L, insumosDespues, "El insumo se eliminó por FK CASCADE");
        org.junit.jupiter.api.Assertions.assertEquals(0L, basesDespues, "La base PERSONAL se eliminó");
    }

    @Test
    void TC_BP_REST_07_borrar_id_inventado_devuelve_404() {
        String token = AuthSupport.registrarConToken(mailbox, "noexiste-borra@ex.com");
        UUID invented = UUID.fromString("0192f6c4-7c8a-7000-8000-000000000999");

        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/bases-personales/" + invented)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    @Test
    void TC_BP_REST_08_borrar_sin_token_devuelve_401() {
        UUID invented = UUID.fromString("0192f6c4-7c8a-7000-8000-000000000999");
        given().when().delete("/api/v1/bases-personales/" + invented).then().statusCode(401);
    }

    private long contarInsumosDeBase(long baseId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT count(*) FROM insumo WHERE base_id = ?")) {
            ps.setLong(1, baseId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long contarBases() throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT count(*) FROM base_insumos");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
