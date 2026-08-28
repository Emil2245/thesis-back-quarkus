package ec.uce.propuestas.insumo.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
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
        try (Connection con = ds.getConnection(); Statement st = con.createStatement()) {
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
}
