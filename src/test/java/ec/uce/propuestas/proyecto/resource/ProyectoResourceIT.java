package ec.uce.propuestas.proyecto.resource;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class ProyectoResourceIT {

    @Inject
    RecordingEnviadorCorreo mailbox;
    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        mailbox.clear();
        try (Connection con = ds.getConnection(); Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, " +
                    "insumo, base_insumos, parametros_proyecto, firmante, proyecto, " +
                    "token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    private Long crearProyecto(String token, String nombre) {
        return ((Number) given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto", nombre,
                        "codigo", "P-2026-01",
                        "anio", (short) 2026,
                        "plazoEjecucion", (short) 6,
                        "plazoUnidad", "MES",
                        "direccionInstitucional", "GAD Municipal"))
                .when().post("/api/v1/proyectos")
                .then().statusCode(201)
                .extract().path("id")).longValue();
    }

    @Test
    void TC_P05_crear_y_listar_propios() {
        String token = AuthSupport.registrarConToken(mailbox, "titular@ex.com");
        Long id = crearProyecto(token, "Puente Tulcán");

        given().header("Authorization", "Bearer " + token)
                .when().get("/api/v1/proyectos")
                .then().statusCode(200)
                .body("items[0].id", equalTo(id.intValue()))
                .body("items[0].nombreProyecto", equalTo("Puente Tulcán"))
                .body("items[0].estado", equalTo("BORRADOR"));
    }

    @Test
    void TC_P05_lista_no_expone_proyectos_ajenos() {
        String titulo = AuthSupport.registrarConToken(mailbox, "titular2@ex.com");
        crearProyecto(titulo, "Vulcano");

        String intruso = AuthSupport.registrarConToken(mailbox, "intruso@ex.com");
        given().header("Authorization", "Bearer " + intruso)
                .when().get("/api/v1/proyectos")
                .then().statusCode(200)
                .body("items.size()", is(0));
    }

    @Test
    void TC_P05_acceso_proyecto_ajeno_devuelve_404() {
        String titular = AuthSupport.registrarConToken(mailbox, "titular2@ex.com");
        Long id = crearProyecto(titular, "SoloDueño");

        String intruso = AuthSupport.registrarConToken(mailbox, "intruso2@ex.com");
        given().header("Authorization", "Bearer " + intruso)
                .when().get("/api/v1/proyectos/" + id)
                .then().statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    @Test
    void TC_P07_parametros_sistema_lectura() {
        String token = AuthSupport.registrarConToken(mailbox, "sistema@ex.com");
        given().header("Authorization", "Bearer " + token)
                .when().get("/api/v1/proyectos/parametros-sistema")
                .then().statusCode(200)
                .body("iva", equalTo(0.1500f));
    }
}