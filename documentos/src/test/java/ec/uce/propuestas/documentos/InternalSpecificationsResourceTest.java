package ec.uce.propuestas.documentos;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(ServiceJwtTestProfile.class)
class InternalSpecificationsResourceTest {
    private static final String PATH = "/internal/v1/render/specifications";
    private static final String PROJECTION = """
            {"schemaVersion":1,"presupuestoId":"01900000-0000-7000-8000-000000000001",
            "nombreProyecto":"Proyecto","anio":2026,"titulo1":"Título","titulo2":"Subtítulo",
            "fechaExportacion":"2026-11-07","especificaciones":[{"codigo":"A-1",
            "descripcion":"Losa","unidad":"u","especificacionTecnica":"Texto técnico"}]}
            """;

    @Test
    void rejectsAnonymousAndInvalidServiceProjection() {
        given().contentType("application/json")
                .body(PROJECTION)
                .post(PATH)
                .then()
                .statusCode(401);
        given().auth()
                .oauth2(ServiceJwtTestProfile.serviceToken())
                .contentType("application/json")
                .body(PROJECTION.replace("\"schemaVersion\":1", "\"schemaVersion\":2"))
                .post(PATH)
                .then()
                .statusCode(400);
        given().auth()
                .oauth2(ServiceJwtTestProfile.serviceToken())
                .contentType("application/json")
                .body(PROJECTION.replace("Texto técnico", "   "))
                .post(PATH)
                .then()
                .statusCode(400);
    }

    @Test
    void rendersDocxOnlyForServiceIdentity() {
        var response = given().auth()
                .oauth2(ServiceJwtTestProfile.serviceToken())
                .contentType("application/json")
                .body(PROJECTION)
                .post(PATH)
                .then()
                .statusCode(200)
                .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .extract()
                .response();
        assertEquals('P', response.asByteArray()[0]);
        assertEquals('K', response.asByteArray()[1]);
    }
}
