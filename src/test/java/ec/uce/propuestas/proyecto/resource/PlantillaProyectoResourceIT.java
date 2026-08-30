package ec.uce.propuestas.proyecto.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.*;

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
            st.execute(
                    "TRUNCATE TABLE plantilla_proyecto, actividad, cronograma, apu_detalle, apu_seccion, apu, rubro, "
                            + "capitulo, presupuesto, insumo, base_insumos, parametros_proyecto, firmante, proyecto, "
                            + "token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    private Long crearProyecto(String token) {
        return ((Number) given().contentType(JSON)
                        .header("Authorization", "Bearer " + token)
                        .body(Map.of(
                                "nombreProyecto", "Proyecto Plantilla",
                                "anio", (short) 2026,
                                "plazoEjecucion", (short) 6,
                                "plazoUnidad", "MES",
                                "direccionInstitucional", "Quito"))
                        .when()
                        .post("/api/v1/proyectos")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("id"))
                .longValue();
    }

    @Test
    void crear_listar_eliminar_plantilla() {
        String token = AuthSupport.registrarConToken(mailbox, "plantilla@uce.edu.ec");
        Long proyectoId = crearProyecto(token);

        // Create plantilla from project
        Number plantillaId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", "Mi plantilla", "descripcion", "Plantilla de prueba", "proyectoId", proyectoId))
                .when()
                .post("/api/v1/plantillas-proyecto")
                .then()
                .statusCode(201)
                .body("nombre", equalTo("Mi plantilla"))
                .body("descripcion", equalTo("Plantilla de prueba"))
                .body("id", notNullValue())
                .extract()
                .path("id");

        // List plantillas
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/plantillas-proyecto")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].nombre", equalTo("Mi plantilla"));

        // Delete plantilla
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/plantillas-proyecto/" + plantillaId)
                .then()
                .statusCode(204);

        // List should be empty
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/plantillas-proyecto")
                .then()
                .statusCode(200)
                .body("size()", is(0));
    }
}
