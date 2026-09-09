package ec.uce.propuestas.insumo.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plan 07 — los path params de {@code /proyectos/{proyectoId}/insumos/*} son
 * UUIDv7 (identidad externa inmutable, columna {@code public_id}). El id del
 * JSON es siempre el {@code publicId} UUIDv7, nunca el {@code BIGINT} interno.
 * UUID mal formado → 400 {@code validacion}; recurso ajeno → 404
 * {@code no-encontrado}.
 */
@QuarkusTest
class InsumoResourceIT {

    private static final String UUID_V7 =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

    /** UUIDv4 (no v7) bien formado — usado para verificar la frontera de validación 400. */
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

    private String crearProyecto(String token) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto", "Riego La Virginia",
                        "anio", (short) 2026,
                        "plazoEjecucion", (short) 4,
                        "plazoUnidad", "MES",
                        "direccionInstitucional", "GAD"))
                .when()
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    @Test
    void TC_P13_crear_y_listar_insumos_de_la_base_del_proyecto() {
        String token = AuthSupport.registrarConToken(mailbox, "insumos@ex.com");
        String proyecto = crearProyecto(token);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "codigo",
                        "MAT-001",
                        "tipo",
                        "MATERIAL",
                        "descripcion",
                        "Cemento",
                        "unidad",
                        "kg",
                        "precioUnitario",
                        0.650000))
                .when()
                .post("/api/v1/proyectos/" + proyecto + "/insumos")
                .then()
                .statusCode(201)
                .body("codigo", equalTo("MAT-001"))
                .body("tipo", equalTo("MATERIAL"))
                .body("id", matchesPattern(UUID_V7));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + proyecto + "/insumos")
                .then()
                .statusCode(200)
                .body("items.size()", is(1))
                .body("total", is(1))
                .body("items[0].id", matchesPattern(UUID_V7));
    }

    @Test
    void TC_P13_codigo_duplicado_en_una_base_devuelve_400() {
        String token = AuthSupport.registrarConToken(mailbox, "dup@ex.com");
        String proyecto = crearProyecto(token);
        String url = "/api/v1/proyectos/" + proyecto + "/insumos";

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "codigo",
                        "DUP-1",
                        "tipo",
                        "MATERIAL",
                        "descripcion",
                        "A",
                        "unidad",
                        "kg",
                        "precioUnitario",
                        1.0))
                .when()
                .post(url)
                .then()
                .statusCode(201);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "codigo",
                        "DUP-1",
                        "tipo",
                        "MATERIAL",
                        "descripcion",
                        "A",
                        "unidad",
                        "kg",
                        "precioUnitario",
                        1.0))
                .when()
                .post(url)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P06_fuerza_unidad_h_para_mano_de_obra() {
        String token = AuthSupport.registrarConToken(mailbox, "mo@ex.com");
        String proyecto = crearProyecto(token);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "codigo",
                        "MO-1",
                        "tipo",
                        "MANO_OBRA",
                        "descripcion",
                        "Peón",
                        "unidad",
                        "h",
                        "precioUnitario",
                        3.50))
                .when()
                .post("/api/v1/proyectos/" + proyecto + "/insumos")
                .then()
                .statusCode(201);
    }

    @Test
    void TC_P07_path_uuid_v4_no_v7_en_path_proyecto_devuelve_400_validacion() {
        String token = AuthSupport.registrarConToken(mailbox, "wu07-ins-v4@ex.com");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + UUID_NO_V7 + "/insumos")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P07_path_uuid_v7_inexistente_en_path_proyecto_devuelve_404() {
        String token = AuthSupport.registrarConToken(mailbox, "wu07-ins-inex@ex.com");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + UUID_INEXISTENTE_V7 + "/insumos")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }
}
