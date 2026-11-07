package ec.uce.propuestas.documentos;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(ServiceJwtTestProfile.class)
class InternalRenderResourceTest {
    private static final String PATH = "/internal/v1/render";

    @Test
    void rejectsAnonymousRenderRequests() {
        given().contentType("application/json")
                .body(validPayload(1, "xlsx"))
                .when()
                .post(PATH)
                .then()
                .statusCode(401);
    }

    @Test
    void rendersBudgetWorkbookForServiceIdentity() {
        var response = given().auth()
                .oauth2(ServiceJwtTestProfile.serviceToken())
                .contentType("application/json")
                .body(validPayload(1, "xlsx"))
                .when()
                .post(PATH)
                .then()
                .statusCode(200)
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .extract()
                .response();
        assertEquals('P', response.asByteArray()[0]);
        assertEquals('K', response.asByteArray()[1]);
    }

    @Test
    void rendersBudgetPdfForServiceIdentity() {
        assertMagic(validPayload(1, "pdf"), "application/pdf", "%PDF");
    }

    @Test
    void rendersApuPdfForServiceIdentity() {
        assertMagic(apuPayload("pdf"), "application/pdf", "%PDF");
    }

    @Test
    void rendersApuWorkbookForServiceIdentity() {
        assertMagic(apuPayload("xlsx"), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "PK");
    }

    @Test
    void rejectsWrongRole() {
        withToken(token("core", "documentos", Set.of("USUARIO"), false, 30), validPayload(1, "xlsx"), 403);
    }

    @Test
    void rejectsWrongIssuer() {
        withToken(token("user", "documentos", Set.of("CORE_SERVICE"), false, 30), validPayload(1, "xlsx"), 401);
    }

    @Test
    void rejectsWrongAudience() {
        withToken(token("core", "otro-servicio", Set.of("CORE_SERVICE"), false, 30), validPayload(1, "xlsx"), 401);
    }

    @Test
    void rejectsExpiredToken() {
        withToken(token("core", "documentos", Set.of("CORE_SERVICE"), false, -120), validPayload(1, "xlsx"), 401);
    }

    @Test
    void rejectsLongLivedServiceToken() {
        withToken(token("core", "documentos", Set.of("CORE_SERVICE"), false, 120), validPayload(1, "xlsx"), 401);
    }

    @Test
    void rejectsUserJwtSignedWithAnotherKey() {
        withToken(token("core", "documentos", Set.of("CORE_SERVICE"), true, 30), validPayload(1, "xlsx"), 401);
    }

    @Test
    void rejectsUnsupportedContractVersion() {
        withToken(ServiceJwtTestProfile.serviceToken(), validPayload(2, "xlsx"), 400);
    }

    @Test
    void rejectsUnsupportedFormat() {
        withToken(ServiceJwtTestProfile.serviceToken(), validPayload(1, "exe"), 400);
    }

    @Test
    void rejectsMissingProjection() {
        withToken(
                ServiceJwtTestProfile.serviceToken(),
                "{\"schemaVersion\":1,\"documento\":\"presupuesto\",\"formato\":\"xlsx\"}",
                400);
    }

    @Test
    void rejectsOversizedProjectionBeforeRendering() {
        // Let the server reject Content-Length before the client streams an oversized body.
        given().auth()
                .oauth2(ServiceJwtTestProfile.serviceToken())
                .header("Expect", "100-continue")
                .contentType("application/json")
                .body(validPayload(1, "xlsx") + " ".repeat(8 * 1024 * 1024))
                .when()
                .post(PATH)
                .then()
                .statusCode(413);
    }

    private static String validPayload(int version, String formato) {
        return """
                {"schemaVersion":%d,"documento":"presupuesto","formato":"%s","opciones":{},
                 "snapshot":{"presupuestoId":"01900000-0000-7000-8000-000000000001","version":1,
                 "vigente":true,"total":0,"proyecto":{"id":"01900000-0000-7000-8000-000000000002",
                 "nombre":"Ejemplo","codigo":"E","anio":2025},"parametros":{"moneda":"USD"},
                 "display":{"precision":2,"precisionPorcentaje":2},"firmantes":[],"capitulos":[],"apus":[]}}
                """.formatted(version, formato);
    }

    private static String apuPayload(String formato) {
        return validPayload(1, formato)
                .replace("\"documento\":\"presupuesto\"", "\"documento\":\"apus\"")
                .replace(
                        "\"opciones\":{}",
                        "\"opciones\":" + ("pdf".equals(formato) ? "{}" : "{\"layout\":\"pestanas\"}"))
                .replace("\"apus\":[]}}", """
                        "apus":[],"apuRender":[{"id":"01900000-0000-7000-8000-000000000001",
                        "codigo":"A-1","etiquetaCodigo":"A-1","descripcion":"APU de prueba","unidad":"m³",
                        "bloques":[],"pie":{"subtotales":[],"porcentajeIndirecto":0,"costoDirecto":1,
                        "costoIndirecto":0,"costoTotal":1,"valorOfertado":1}}]}}
                        """);
    }

    private static void assertMagic(String payload, String contentType, String magic) {
        var response = given().auth()
                .oauth2(ServiceJwtTestProfile.serviceToken())
                .contentType("application/json")
                .body(payload)
                .when()
                .post(PATH)
                .then()
                .statusCode(200)
                .contentType(contentType)
                .extract()
                .response();
        var bytes = response.asByteArray();
        for (int i = 0; i < magic.length(); i++) assertEquals(magic.charAt(i), bytes[i]);
    }

    private static String token(String issuer, String audience, Set<String> roles, boolean userKey, long seconds) {
        return ServiceJwtTestProfile.token(
                issuer, audience, roles, Instant.now().plusSeconds(seconds).getEpochSecond(), userKey);
    }

    private static void withToken(String token, String payload, int status) {
        given().auth()
                .oauth2(token)
                .contentType("application/json")
                .body(payload)
                .when()
                .post(PATH)
                .then()
                .statusCode(status);
    }
}
