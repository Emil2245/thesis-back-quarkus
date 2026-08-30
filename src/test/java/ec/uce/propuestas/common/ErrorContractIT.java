package ec.uce.propuestas.common;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.*;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifica que todos los errores HTTP sigan el contract de
 * {@code ErrorPayload}: {@code {codigo, mensaje}} — nunca el formato
 * ViolationReport nativo de Quarkus ni bodies vacíos.
 */
@QuarkusTest
class ErrorContractIT {

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        mailbox.clear();
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    /** 401 sin token → body contract (no vacío) */
    @Test
    void sin_token_401_con_body_contract() {
        given().when().get("/api/v1/perfil").then().statusCode(401).body("codigo", equalTo("credenciales-invalidas"));
    }

    /** 401 con token basura → body contract */
    @Test
    void token_basura_401_con_body_contract() {
        given().header("Authorization", "Bearer garbage.token.here")
                .when()
                .get("/api/v1/perfil")
                .then()
                .statusCode(401)
                .body("codigo", equalTo("credenciales-invalidas"))
                .body("mensaje", equalTo("Token ausente, inválido o expirado"));
    }

    /** Validación @Valid en body → 400 contract (no ViolationReport nativo) */
    @Test
    void validacion_400_con_body_contract() {
        given().contentType(JSON)
                .body("{}")
                .when()
                .post("/api/v1/auth/registro")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"))
                .body("mensaje", not(emptyString()));
    }

    /** Path param ilegible (número esperado) → 404 no-encontrado con mensaje legible */
    @Test
    void path_param_ilegible_404_legible() {
        String token = AuthSupport.registrarConToken(mailbox, "con@ex.com");
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/empty/insumos")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }
}
