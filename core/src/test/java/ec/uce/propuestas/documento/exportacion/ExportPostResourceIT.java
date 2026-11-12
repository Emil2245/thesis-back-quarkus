package ec.uce.propuestas.documento.exportacion;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ExportPostResourceIT {
    private static final String BUDGET = "01900000-0000-7000-8000-000000000099";
    private static final String KEY = "01900000-0000-7000-8000-000000000088";

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Test
    void publicPostRejectsMissingKeyAndDuplicateBodyBeforeAnyCapture() {
        String token = AuthSupport.registrarConToken(mailbox, "stage03-post-" + System.nanoTime() + "@test.ec");
        String path = "/api/v1/documentos/presupuesto/" + BUDGET + "/exportaciones";
        assertEquals(
                400,
                given().auth()
                        .oauth2(token)
                        .contentType("application/json")
                        .body("{\"formato\":\"pdf\"}")
                        .post(path)
                        .statusCode());
        assertEquals(
                400,
                given().auth()
                        .oauth2(token)
                        .contentType("application/json")
                        .header("Idempotency-Key", KEY)
                        .body("{\"formato\":\"xlsx\",\"formato\":\"pdf\"}")
                        .post(path)
                        .statusCode());
        assertEquals(
                401,
                given().contentType("application/json")
                        .header("Idempotency-Key", KEY)
                        .body("{\"formato\":\"pdf\"}")
                        .post(path)
                        .statusCode());
    }
}
