package ec.uce.propuestas.insumo.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plan 044 (bugs-pendientes §5) — insumos de bases PERSONALES, lectura de
 * insumos de bases CENTRALES y copia PERSONAL → PROYECTO. Lo que se prueba es
 * lo que puede romper datos o filtrar existencia: el owner-scope (RNF-05).
 */
@QuarkusTest
class BasesPersonalesInsumosIT {

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        mailbox.clear();
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE log_actividad, apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, "
                    + "insumo, base_insumos, parametros_proyecto, firmante, proyecto, "
                    + "token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    private static Map<String, Object> insumo(String codigo) {
        return Map.of(
                "codigo",
                codigo,
                "tipo",
                "MATERIAL",
                "descripcion",
                "Cemento " + codigo,
                "unidad",
                "kg",
                "precioUnitario",
                7.5);
    }

    private static String crearBase(String token, String nombre) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", nombre))
                .when()
                .post("/api/v1/bases-personales")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    @Test
    void TC_BPI_01_duenno_crea_lista_edita_y_elimina_insumos() {
        String token = AuthSupport.registrarConToken(mailbox, "bpi-owner@ex.com");
        String baseId = crearBase(token, "Mi base");

        String insumoId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(insumo("MAT-1"))
                .when()
                .post("/api/v1/bases-personales/" + baseId + "/insumos")
                .then()
                .statusCode(201)
                .body("codigo", equalTo("MAT-1"))
                .extract()
                .path("id");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/bases-personales/" + baseId + "/insumos")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("total", equalTo(1));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "Cemento gris", "unidad", "saco", "precioUnitario", 8))
                .when()
                .put("/api/v1/bases-personales/" + baseId + "/insumos/" + insumoId)
                .then()
                .statusCode(200)
                .body("descripcion", equalTo("Cemento gris"));

        // Código duplicado en la misma base: misma validación que la base PROYECTO.
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(insumo("MAT-1"))
                .when()
                .post("/api/v1/bases-personales/" + baseId + "/insumos")
                .then()
                .statusCode(400);

        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/bases-personales/" + baseId + "/insumos/" + insumoId)
                .then()
                .statusCode(204);
    }

    @Test
    void TC_BPI_02_base_ajena_devuelve_404_en_lectura_y_escritura() {
        String tokenAlice = AuthSupport.registrarConToken(mailbox, "bpi-alice@ex.com");
        String tokenBob = AuthSupport.registrarConToken(mailbox, "bpi-bob@ex.com");
        String baseAlice = crearBase(tokenAlice, "Privada");
        String insumoAlice = given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenAlice)
                .body(insumo("MAT-A"))
                .when()
                .post("/api/v1/bases-personales/" + baseAlice + "/insumos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given().header("Authorization", "Bearer " + tokenBob)
                .when()
                .get("/api/v1/bases-personales/" + baseAlice + "/insumos")
                .then()
                .statusCode(404);
        given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenBob)
                .body(insumo("MAT-B"))
                .when()
                .post("/api/v1/bases-personales/" + baseAlice + "/insumos")
                .then()
                .statusCode(404);
        given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenBob)
                .body(Map.of("descripcion", "Robado", "unidad", "kg", "precioUnitario", 1))
                .when()
                .put("/api/v1/bases-personales/" + baseAlice + "/insumos/" + insumoAlice)
                .then()
                .statusCode(404);
        given().header("Authorization", "Bearer " + tokenBob)
                .when()
                .delete("/api/v1/bases-personales/" + baseAlice + "/insumos/" + insumoAlice)
                .then()
                .statusCode(404);
    }

    @Test
    void TC_BPI_03_base_proyecto_no_es_accesible_por_la_ruta_personal() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "bpi-proy@ex.com");
        String proyectoId = crearProyecto(token);
        // Materializa la base PROYECTO y lee su public_id.
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + proyectoId + "/insumos")
                .then()
                .statusCode(200);
        String baseProyecto;
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT public_id FROM base_insumos WHERE tipo = 'PROYECTO'");
                var rs = ps.executeQuery()) {
            rs.next();
            baseProyecto = rs.getString(1);
        }

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/bases-personales/" + baseProyecto + "/insumos")
                .then()
                .statusCode(404);
    }

    @Test
    void TC_BPI_04_usuario_lee_insumos_de_base_central_pero_no_de_una_personal() {
        String adminToken = registrarSuperAdmin("bpi-admin@ex.com");
        String central = given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("nombre", "Central 2026"))
                .when()
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(insumo("CEN-1"))
                .when()
                .post("/api/v1/admin/bases-centrales/" + central + "/insumos")
                .then()
                .statusCode(201);

        String token = AuthSupport.registrarConToken(mailbox, "bpi-lector@ex.com");
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/bases-centrales/" + central + "/insumos")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].codigo", equalTo("CEN-1"));

        String personal = crearBase(token, "No es central");
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/bases-centrales/" + personal + "/insumos")
                .then()
                .statusCode(404);
    }

    @Test
    void TC_BPI_05_copiar_base_personal_propia_al_proyecto_y_ajena_404() {
        String tokenAlice = AuthSupport.registrarConToken(mailbox, "bpi-copia-a@ex.com");
        String tokenBob = AuthSupport.registrarConToken(mailbox, "bpi-copia-b@ex.com");
        String base = crearBase(tokenAlice, "Para copiar");
        given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenAlice)
                .body(insumo("MAT-C"))
                .when()
                .post("/api/v1/bases-personales/" + base + "/insumos")
                .then()
                .statusCode(201);
        String proyectoAlice = crearProyecto(tokenAlice);
        String proyectoBob = crearProyecto(tokenBob);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenAlice)
                .body(Map.of("fuenteTipo", "PERSONAL", "baseId", base))
                .when()
                .post("/api/v1/proyectos/" + proyectoAlice + "/insumos/copiar")
                .then()
                .statusCode(200)
                .body("copiados", equalTo(1));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenBob)
                .body(Map.of("fuenteTipo", "PERSONAL", "baseId", base))
                .when()
                .post("/api/v1/proyectos/" + proyectoBob + "/insumos/copiar")
                .then()
                .statusCode(404);
    }

    private String crearProyecto(String token) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto", "Obra",
                        "anio", 2026,
                        "plazoEjecucion", 4,
                        "plazoUnidad", "MES",
                        "direccionInstitucional", "UCE"))
                .when()
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String registrarSuperAdmin(String email) {
        AuthSupport.registrarConToken(mailbox, email);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE usuario SET rol = 'SUPER_ADMIN' WHERE email = ?")) {
            ps.setString(1, email);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Response login = given().contentType(JSON)
                .body(Map.of("email", email, "password", "Pass1234", "recordarSesion", false))
                .when()
                .post("/api/v1/auth/login");
        login.then().statusCode(200);
        return login.path("accessToken");
    }
}
