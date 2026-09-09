package ec.uce.propuestas.common.config;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * TDD T3 — Override de {@code app.display.*} vía {@link QuarkusTestProfile}.
 * Demuestra que las claves {@code precision} y {@code precision-porcentaje}
 * se mapean a las propiedades de la respuesta JSON {@code precisionDinero}
 * y {@code precisionPorcentaje} (no se hardcodea el prefijo {@code /api/v1}).
 */
@QuarkusTest
@TestProfile(DisplayConfigResourceOverrideTest.DisplayOverrideProfile.class)
class DisplayConfigResourceOverrideTest {

    public static final class DisplayOverrideProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "app.display.precision", "3",
                    "app.display.precision-porcentaje", "6");
        }
    }

    @Test
    void get_display_config_returns_overridden_3_and_6() {
        given().accept(MediaType.APPLICATION_JSON)
                .when()
                .get("/api/v1/config/display")
                .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON)
                .body("precisionDinero", is(3))
                .body("precisionPorcentaje", is(equalTo(6)));
    }
}
