package ec.uce.propuestas.insumo.resource;

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
class BasePersonalResourceIT {

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
                    + "insumo, base_insumos, parametros_proyecto, firmante, proyecto, token_usuario, "
                    + "refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    @Test
    void crear_listar_base_personal() {
        String token = AuthSupport.registrarConToken(mailbox, "personal@uce.edu.ec");

        // Create personal base
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", "Mis insumos frecuentes"))
                .when()
                .post("/api/v1/bases-personales")
                .then()
                .statusCode(201)
                .body("nombre", equalTo("Mis insumos frecuentes"))
                .body("tipo", equalTo("PERSONAL"))
                .body("totalInsumos", is(0));

        // List personal bases
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/bases-personales")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].nombre", equalTo("Mis insumos frecuentes"))
                .body("[0].tipo", equalTo("PERSONAL"));
    }

    @Test
    void bases_personales_aisladas_por_usuario() {
        String token1 = AuthSupport.registrarConToken(mailbox, "user1@uce.edu.ec");
        String token2 = AuthSupport.registrarConToken(mailbox, "user2@uce.edu.ec");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token1)
                .body(Map.of("nombre", "Base de user1"))
                .when()
                .post("/api/v1/bases-personales")
                .then()
                .statusCode(201);

        // User2 should see empty list
        given().header("Authorization", "Bearer " + token2)
                .when()
                .get("/api/v1/bases-personales")
                .then()
                .statusCode(200)
                .body("size()", is(0));

        // User1 should see their base
        given().header("Authorization", "Bearer " + token1)
                .when()
                .get("/api/v1/bases-personales")
                .then()
                .statusCode(200)
                .body("size()", is(1));
    }
}
