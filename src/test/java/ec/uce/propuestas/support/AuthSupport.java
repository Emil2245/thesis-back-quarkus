package ec.uce.propuestas.support;

import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import ec.uce.propuestas.usuario.auth.dto.LoginRequest;
import ec.uce.propuestas.usuario.auth.dto.RegistroRequest;
import ec.uce.propuestas.usuario.auth.dto.VerificarEmailRequest;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;

/**
 * Helpers de integración para pruebas REST: registra, verifica y loguea un
 * usuario de forma aislada por email, devolviendo el access token.
 */
public final class AuthSupport {

    private AuthSupport() {
    }

    public static String registrarConToken(RecordingEnviadorCorreo mailbox, String email) {
        String nombre = "Titular " + email;
        given().contentType(JSON)
                .body(new RegistroRequest(nombre, email, "Pass1234", "Pass1234"))
                .when().post("/api/v1/auth/registro")
                .then().statusCode(201);

        var raw = mailbox.entregas().stream()
                .filter(e -> e.tipo().equals("verificacion") && e.destinatario().equals(email))
                .findFirst().orElseThrow().tokenRaw();
        given().contentType(JSON).body(new VerificarEmailRequest(raw))
                .when().post("/api/v1/auth/verificar-email")
                .then().statusCode(204);

        return given().contentType(JSON).body(new LoginRequest(email, "Pass1234", false))
                .when().post("/api/v1/auth/login")
                .then().statusCode(200)
                .extract().path("accessToken");
    }
}