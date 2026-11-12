package ec.uce.propuestas.documentos;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

/**
 * Stage 05: Core no longer asks Documentos for bytes. The only service boundary is the private
 * export upload ({@code /internal/v1/export}); the synchronous render routes are gone even for a
 * valid service identity.
 */
@QuarkusTest
@TestProfile(ServiceJwtTestProfile.class)
class LegacyRenderRoutesRetiredTest {
    private static final String BUDGET = """
            {"schemaVersion":1,"documento":"presupuesto","formato":"xlsx","opciones":{},
             "snapshot":{"presupuestoId":"01900000-0000-7000-8000-000000000001","version":1,
             "vigente":true,"total":0,"proyecto":{"id":"01900000-0000-7000-8000-000000000002",
             "nombre":"Ejemplo","codigo":"E","anio":2025},"parametros":{"moneda":"USD"},
             "display":{"precision":2,"precisionPorcentaje":2},"firmantes":[],"capitulos":[],"apus":[]}}
            """;
    private static final String SPECIFICATION = """
            {"schemaVersion":1,"presupuestoId":"01900000-0000-7000-8000-000000000001",
            "nombreProyecto":"Proyecto","anio":2026,"titulo1":"Título","titulo2":"Subtítulo",
            "fechaExportacion":"2026-11-07","especificaciones":[{"codigo":"A-1",
            "descripcion":"Losa","unidad":"u","especificacionTecnica":"Texto técnico"}]}
            """;

    @Test
    void synchronousBudgetAndApuRenderRouteIsRetired() {
        given().auth()
                .oauth2(ServiceJwtTestProfile.serviceToken())
                .contentType("application/json")
                .body(BUDGET)
                .when()
                .post("/internal/v1/render")
                .then()
                .statusCode(404);
    }

    @Test
    void synchronousSpecificationsRenderRouteIsRetired() {
        given().auth()
                .oauth2(ServiceJwtTestProfile.serviceToken())
                .contentType("application/json")
                .body(SPECIFICATION)
                .when()
                .post("/internal/v1/render/specifications")
                .then()
                .statusCode(404);
    }
}
