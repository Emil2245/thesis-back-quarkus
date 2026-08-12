package ec.uce.propuestas.insumo.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

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

@QuarkusTest
class InsumoResourceIT {

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

    private Long crearProyecto(String token) {
        return ((Number) given().contentType(JSON)
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
                        .path("id"))
                .longValue();
    }

    @Test
    void TC_P13_crear_y_listar_insumos_de_la_base_del_proyecto() {
        String token = AuthSupport.registrarConToken(mailbox, "insumos@ex.com");
        Long proyecto = crearProyecto(token);

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
                .body("tipo", equalTo("MATERIAL"));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + proyecto + "/insumos")
                .then()
                .statusCode(200)
                .body("items.size()", is(1))
                .body("total", is(1));
    }

    @Test
    void TC_P13_codigo_duplicado_en_una_base_devuelve_400() {
        String token = AuthSupport.registrarConToken(mailbox, "dup@ex.com");
        Long proyecto = crearProyecto(token);
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
        Long proyecto = crearProyecto(token);

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
}
