package ec.uce.propuestas.documento;

import static io.restassured.RestAssured.given;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Stage 05: the byte GETs rendered by Core were retired in favour of
 * {@code POST /documentos/{documento}/{id}/exportaciones} plus a one-use ticket.
 * No resource method matches the bare document path any more, so it is 404; the
 * preflights under the same prefix stay available.
 */
@QuarkusTest
class LegacyByteDownloadRetiredIT {
    @Inject
    RecordingEnviadorCorreo mailbox;

    private String[] ownedBudget() {
        String token = AuthSupport.registrarConToken(mailbox, "retired-" + System.nanoTime() + "@test.ec");
        String project = given().contentType("application/json")
                .auth()
                .oauth2(token)
                .body(Map.of(
                        "nombreProyecto",
                        "Retired",
                        "codigo",
                        "R" + System.nanoTime(),
                        "anio",
                        2026,
                        "plazoEjecucion",
                        6,
                        "plazoUnidad",
                        "MES",
                        "direccionInstitucional",
                        "UCE"))
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        String budget = given().auth()
                .oauth2(token)
                .get("/api/v1/proyectos/" + project + "/presupuestos")
                .then()
                .statusCode(200)
                .extract()
                .path("[0].presupuestoId");
        // A real cronograma, so a 404 here can only mean "no such route", never
        // the business 404 for a budget without schedule.
        given().contentType("application/json")
                .auth()
                .oauth2(token)
                .body(Map.of("unidadTiempo", "MES", "numeroPeriodos", 6))
                .post("/api/v1/presupuestos/" + budget + "/cronograma")
                .then()
                .statusCode(org.hamcrest.Matchers.either(org.hamcrest.Matchers.is(200))
                        .or(org.hamcrest.Matchers.is(201)));
        return new String[] {token, budget};
    }

    @ParameterizedTest
    @CsvSource({
        "presupuesto, formato=xlsx",
        "apus, formato=pdf",
        "cronograma, formato=xlsx",
        "especificaciones-tecnicas, formato=docx"
    })
    void legacyByteGetIsGoneWhilePreflightStays(String documento, String query) {
        String[] owned = ownedBudget();
        given().auth()
                .oauth2(owned[0])
                .get("/api/v1/documentos/" + documento + "/" + owned[1] + "?" + query)
                .then()
                .statusCode(404);
        if (!documento.equals("especificaciones-tecnicas")) {
            given().auth()
                    .oauth2(owned[0])
                    .get("/api/v1/documentos/" + documento + "/" + owned[1] + "/preflight?" + query)
                    .then()
                    .statusCode(200);
        }
    }
}
