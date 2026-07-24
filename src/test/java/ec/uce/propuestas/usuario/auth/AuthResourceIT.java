package ec.uce.propuestas.usuario.auth;

import ec.uce.propuestas.usuario.auth.dto.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AuthResourceIT {

    @Inject RecordingEnviadorCorreo mailbox;
    @Inject DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        mailbox.clear();
        try (Connection con = ds.getConnection();
             Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    // =========================================================================
    // TC-P01: Registration and email verification
    // =========================================================================

    /** TC-P01-01: Registro completo + verificación exitosa */
    @Test
    void TC_P01_01_registro_y_verificacion_completo() {
        // Register
        given().contentType(JSON)
            .body(new RegistroRequest("Ana", "ana@ex.com", "Pass1234", "Pass1234"))
            .when().post("/api/v1/auth/registro")
            .then().statusCode(201)
            .body("emailVerificado", is(false))
            .body("email", equalTo("ana@ex.com"));

        // Extract verification token from mailbox
        var token = mailbox.entregas().stream()
            .filter(e -> e.tipo().equals("verificacion"))
            .findFirst().orElseThrow().tokenRaw();

        // Verify email
        given().contentType(JSON)
            .body(new VerificarEmailRequest(token))
            .when().post("/api/v1/auth/verificar-email")
            .then().statusCode(204);
    }

    /** TC-P01-02: Email duplicado devuelve 400 */
    @Test
    void TC_P01_02_email_duplicado() {
        given().contentType(JSON)
            .body(new RegistroRequest("Ana", "dup@ex.com", "Pass1234", "Pass1234"))
            .when().post("/api/v1/auth/registro")
            .then().statusCode(201);

        given().contentType(JSON)
            .body(new RegistroRequest("Ana2", "dup@ex.com", "Pass1234", "Pass1234"))
            .when().post("/api/v1/auth/registro")
            .then().statusCode(400)
            .body("codigo", equalTo("validacion"));
    }

    /** TC-P01-03: Reenvío de verificación con cooldown activo devuelve 429 */
    @Test
    void TC_P01_03_reenvio_cooldown_429() {
        given().contentType(JSON)
            .body(new RegistroRequest("Bob", "bob@ex.com", "Pass1234", "Pass1234"))
            .when().post("/api/v1/auth/registro")
            .then().statusCode(201);

        // Immediately after registration a token was already created;
        // any reenvio within the 60 s cooldown window must return 429.
        given().contentType(JSON)
            .body(new ReenviarVerificacionRequest("bob@ex.com"))
            .when().post("/api/v1/auth/reenviar-verificacion")
            .then().statusCode(429)
            .body("codigo", equalTo("cooldown-activo"));
    }

    /** TC-P01-04: Token expirado devuelve 410 */
    @Test
    void TC_P01_04_token_expirado_o_invalido() {
        given().contentType(JSON)
            .body(new VerificarEmailRequest("tokenFalso-que-no-existe"))
            .when().post("/api/v1/auth/verificar-email")
            .then().statusCode(410)
            .body("codigo", equalTo("token-invalido-o-expirado"));
    }

    /** TC-P01-05: Password no cumple política devuelve 400 */
    @Test
    void TC_P01_05_password_politica_400() {
        given().contentType(JSON)
            .body(new RegistroRequest("Carl", "carl@ex.com", "corta", "corta"))
            .when().post("/api/v1/auth/registro")
            .then().statusCode(400)
            .body("codigo", equalTo("validacion"));
    }

    // =========================================================================
    // TC-P02: Login
    // =========================================================================

    /** TC-P02-01: Login exitoso tras verificar email */
    @Test
    void TC_P02_01_login_exitoso() {
        registrarYVerificar("diana@ex.com", "Diana", "Pass1234");

        given().contentType(JSON)
            .body(new LoginRequest("diana@ex.com", "Pass1234", false))
            .when().post("/api/v1/auth/login")
            .then().statusCode(200)
            .body("accessToken", notNullValue())
            .body("refreshToken", notNullValue())
            .body("usuario.emailVerificado", is(true));
    }

    /** TC-P02-02: Password incorrecto devuelve 401 */
    @Test
    void TC_P02_02_password_incorrecto_401() {
        registrarYVerificar("edu@ex.com", "Eduardo", "Pass1234");

        given().contentType(JSON)
            .body(new LoginRequest("edu@ex.com", "WrongPass9", false))
            .when().post("/api/v1/auth/login")
            .then().statusCode(401)
            .body("codigo", equalTo("credenciales-invalidas"));
    }

    /** TC-P02-03: Email inexistente devuelve 401 (anti-enumeración, mismo código) */
    @Test
    void TC_P02_03_email_inexistente_401() {
        given().contentType(JSON)
            .body(new LoginRequest("noexiste@ex.com", "Pass1234", false))
            .when().post("/api/v1/auth/login")
            .then().statusCode(401)
            .body("codigo", equalTo("credenciales-invalidas"));
    }

    /** TC-P02-04: Login con email no verificado devuelve 403 email-no-verificado */
    @Test
    void TC_P02_04_email_no_verificado_403() {
        given().contentType(JSON)
            .body(new RegistroRequest("Fiona", "fiona@ex.com", "Pass1234", "Pass1234"))
            .when().post("/api/v1/auth/registro")
            .then().statusCode(201);

        given().contentType(JSON)
            .body(new LoginRequest("fiona@ex.com", "Pass1234", false))
            .when().post("/api/v1/auth/login")
            .then().statusCode(403)
            .body("codigo", equalTo("email-no-verificado"));
    }

    /** TC-P02-05: Refresh token rota correctamente */
    @Test
    void TC_P02_05_refresh_token_rotacion() {
        registrarYVerificar("greta@ex.com", "Greta", "Pass1234");

        var loginResp = given().contentType(JSON)
            .body(new LoginRequest("greta@ex.com", "Pass1234", true))
            .when().post("/api/v1/auth/login")
            .then().statusCode(200).extract().body().as(TokenResponse.class);

        assertNotNull(loginResp.refreshToken());

        // Refresh
        var refreshResp = given().contentType(JSON)
            .body(new RefreshRequest(loginResp.refreshToken()))
            .when().post("/api/v1/auth/refresh")
            .then().statusCode(200)
            .body("accessToken", notNullValue())
            .body("refreshToken", notNullValue())
            .extract().body().as(TokenResponse.class);

        // Old refresh token must be invalid now
        given().contentType(JSON)
            .body(new RefreshRequest(loginResp.refreshToken()))
            .when().post("/api/v1/auth/refresh")
            .then().statusCode(401);
    }

    // =========================================================================
    // TC-P03: Password recovery
    // =========================================================================

    /** TC-P03-01: Recuperar con email inexistente siempre devuelve 202 */
    @Test
    void TC_P03_01_recuperar_email_inexistente_202() {
        given().contentType(JSON)
            .body(new RecuperarPasswordRequest("noexiste@ex.com"))
            .when().post("/api/v1/auth/recuperar")
            .then().statusCode(202);
    }

    /** TC-P03-02: Recuperar con email existente: 202 + token enviado */
    @Test
    void TC_P03_02_recuperar_email_existente_202() {
        registrarYVerificar("hugo@ex.com", "Hugo", "Pass1234");
        mailbox.clear();

        given().contentType(JSON)
            .body(new RecuperarPasswordRequest("hugo@ex.com"))
            .when().post("/api/v1/auth/recuperar")
            .then().statusCode(202);

        var resetTokens = mailbox.entregas().stream()
            .filter(e -> e.tipo().equals("reset")).toList();
        assertEquals(1, resetTokens.size());
    }

    /** TC-P03-03: Restablecer con token válido: 204 + login con nueva password */
    @Test
    void TC_P03_03_restablecer_password_exitoso() {
        registrarYVerificar("iris@ex.com", "Iris", "OldPass1");
        mailbox.clear();

        given().contentType(JSON)
            .body(new RecuperarPasswordRequest("iris@ex.com"))
            .when().post("/api/v1/auth/recuperar")
            .then().statusCode(202);

        var raw = mailbox.entregas().stream()
            .filter(e -> e.tipo().equals("reset"))
            .findFirst().orElseThrow().tokenRaw();

        given().contentType(JSON)
            .body(new RestablecerPasswordRequest(raw, "NewPass99", "NewPass99"))
            .when().post("/api/v1/auth/restablecer")
            .then().statusCode(204);

        // Login with new password
        given().contentType(JSON)
            .body(new LoginRequest("iris@ex.com", "NewPass99", false))
            .when().post("/api/v1/auth/login")
            .then().statusCode(200);
    }

    /** TC-P03-04: Restablecer con token inválido devuelve 410 */
    @Test
    void TC_P03_04_restablecer_token_invalido_410() {
        given().contentType(JSON)
            .body(new RestablecerPasswordRequest("tokenFalso999", "NewPass99", "NewPass99"))
            .when().post("/api/v1/auth/restablecer")
            .then().statusCode(410)
            .body("codigo", equalTo("token-invalido-o-expirado"));
    }

    // =========================================================================
    // TC-P04: Profile
    // =========================================================================

    /** TC-P04-01: Leer perfil autenticado */
    @Test
    void TC_P04_01_leer_perfil() {
        registrarYVerificar("juana@ex.com", "Juana", "Pass1234");

        String token = loginAndGetToken("juana@ex.com", "Pass1234");

        given().header("Authorization", "Bearer " + token)
            .when().get("/api/v1/perfil")
            .then().statusCode(200)
            .body("email", equalTo("juana@ex.com"))
            .body("nombre", equalTo("Juana"));
    }

    /** TC-P04-02: Leer perfil sin token devuelve 401 */
    @Test
    void TC_P04_02_perfil_sin_token_401() {
        given().when().get("/api/v1/perfil")
            .then().statusCode(401);
    }

    /** TC-P04-03: Actualizar nombre del perfil */
    @Test
    void TC_P04_03_actualizar_nombre_perfil() {
        registrarYVerificar("karl@ex.com", "Karl", "Pass1234");
        String token = loginAndGetToken("karl@ex.com", "Pass1234");

        given().contentType(JSON)
            .header("Authorization", "Bearer " + token)
            .body(new PerfilActualizarRequest("Karl Updated", "karl@ex.com"))
            .when().put("/api/v1/perfil")
            .then().statusCode(200)
            .body("nombre", equalTo("Karl Updated"));
    }

    /** TC-P04-04: Cambiar contraseña con password actual correcto */
    @Test
    void TC_P04_04_cambiar_password_exitoso() {
        registrarYVerificar("lena@ex.com", "Lena", "Pass1234");
        String token = loginAndGetToken("lena@ex.com", "Pass1234");

        given().contentType(JSON)
            .header("Authorization", "Bearer " + token)
            .body(new PasswordCambiarRequest("Pass1234", "NewPass99", "NewPass99"))
            .when().put("/api/v1/perfil/password")
            .then().statusCode(204);

        // Old password must not work anymore
        given().contentType(JSON)
            .body(new LoginRequest("lena@ex.com", "Pass1234", false))
            .when().post("/api/v1/auth/login")
            .then().statusCode(401);

        // New password works
        given().contentType(JSON)
            .body(new LoginRequest("lena@ex.com", "NewPass99", false))
            .when().post("/api/v1/auth/login")
            .then().statusCode(200);
    }

    /** TC-P04-05: Cambiar contraseña con password actual incorrecto devuelve 401 */
    @Test
    void TC_P04_05_cambiar_password_incorrecto_401() {
        registrarYVerificar("mario@ex.com", "Mario", "Pass1234");
        String token = loginAndGetToken("mario@ex.com", "Pass1234");

        given().contentType(JSON)
            .header("Authorization", "Bearer " + token)
            .body(new PasswordCambiarRequest("WrongPass9", "NewPass99", "NewPass99"))
            .when().put("/api/v1/perfil/password")
            .then().statusCode(401);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void registrarYVerificar(String email, String nombre, String password) {
        given().contentType(JSON)
            .body(new RegistroRequest(nombre, email, password, password))
            .when().post("/api/v1/auth/registro")
            .then().statusCode(201);

        var token = mailbox.entregas().stream()
            .filter(e -> e.tipo().equals("verificacion") && e.destinatario().equals(email))
            .findFirst().orElseThrow().tokenRaw();

        given().contentType(JSON)
            .body(new VerificarEmailRequest(token))
            .when().post("/api/v1/auth/verificar-email")
            .then().statusCode(204);
    }

    private String loginAndGetToken(String email, String password) {
        return given().contentType(JSON)
            .body(new LoginRequest(email, password, false))
            .when().post("/api/v1/auth/login")
            .then().statusCode(200)
            .extract().path("accessToken");
    }
}
