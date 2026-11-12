package ec.uce.propuestas.documentos;

import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DocumentosHealthTest {
    @Test
    void startsWithoutPostgreSqlAndReportsHealthy() {
        RestAssured.get("/q/health").then().statusCode(200).body("status", equalTo("UP"));
    }
}
