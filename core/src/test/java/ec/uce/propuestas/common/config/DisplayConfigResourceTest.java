package ec.uce.propuestas.common.config;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

/**
 * TDD T3 — {@code GET /api/v1/config/display} con los defaults
 * {@code 2/4} declarados en {@code application.yml} (placeholders
 * {@code DISPLAY_PRECISION} / {@code DISPLAY_PRECISION_PORCENTAJE}).
 *
 * <p>Sin {@code @TestProfile}: este test hereda los defaults de
 * {@code application.yml}. El companion {@link DisplayConfigResourceOverrideTest}
 * verifica el override vía {@code QuarkusTestProfile}.
 */
@QuarkusTest
class DisplayConfigResourceTest {

    @Test
    void get_display_config_returns_defaults_2_and_4() {
        given().accept(MediaType.APPLICATION_JSON)
                .when()
                .get("/api/v1/config/display")
                .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON)
                .body("precisionDinero", is(2))
                .body("precisionPorcentaje", is(equalTo(4)));
    }
}
