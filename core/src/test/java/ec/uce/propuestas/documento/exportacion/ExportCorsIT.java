package ec.uce.propuestas.documento.exportacion;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ExportCorsIT {
    private static final String PATH =
            "/api/v1/documentos/presupuesto/01900000-0000-7000-8000-000000000099/exportaciones";

    @Test
    void authorizedFrontendMayPreflightAuthenticatedIdempotentPost() {
        given().header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key")
                .when()
                .options(PATH)
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", equalTo("http://localhost:5173"))
                .header("Access-Control-Allow-Methods", containsStringIgnoringCase("POST"))
                .header("Access-Control-Allow-Headers", containsStringIgnoringCase("authorization"))
                .header("Access-Control-Allow-Headers", containsStringIgnoringCase("content-type"))
                .header("Access-Control-Allow-Headers", containsStringIgnoringCase("idempotency-key"));
    }

    @Test
    void existingGetPreflightRetainsAuthorizationAndContentType() {
        given().header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .when()
                .options("/api/v1/documentos/presupuesto/01900000-0000-7000-8000-000000000099/preflight")
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", equalTo("http://localhost:5173"))
                .header("Access-Control-Allow-Headers", containsStringIgnoringCase("authorization"))
                .header("Access-Control-Allow-Headers", containsStringIgnoringCase("content-type"));
    }

    @Test
    void disallowedMethodIsNotExposedToTheBrowser() {
        given().header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "TRACE")
                .header("Access-Control-Request-Headers", "idempotency-key")
                .when()
                .options(PATH)
                .then()
                .header("Access-Control-Allow-Methods", not(containsStringIgnoringCase("TRACE")));
    }

    @Test
    void foreignOriginDoesNotGainCORSAccess() {
        given().header("Origin", "https://not-allowed.example")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key")
                .when()
                .options(PATH)
                .then()
                .header("Access-Control-Allow-Origin", nullValue());
    }
}
