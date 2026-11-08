package ec.uce.propuestas.documentos;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ec.uce.propuestas.documentos.storage.S3PrivateObjectStore;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@QuarkusTest
@TestProfile(ServiceJwtTestProfile.class)
class InternalExportResourceTest {
    private static final String PATH = "/internal/v1/export";
    private static final String OPERATION_ID = "01900000-0000-7000-8000-000000000001";
    private static final String KEY = "exports/" + OPERATION_ID;
    private static final String FINGERPRINT = "a".repeat(64);
    private static final String PAYLOAD = """
            {"schemaVersion":1,"key":"exports/01900000-0000-7000-8000-000000000001",
             "operationId":"01900000-0000-7000-8000-000000000001",
             "fingerprint":"%s","filename":"budget.pdf",
             "projection":{"schemaVersion":1,"documento":"presupuesto","formato":"pdf","opciones":{},
              "snapshot":{"presupuestoId":"01900000-0000-7000-8000-000000000001","version":1,
              "vigente":true,"total":0,"proyecto":{"id":"01900000-0000-7000-8000-000000000002",
              "nombre":"Example","codigo":"E","anio":2025},"parametros":{"moneda":"USD"},
              "display":{"precision":2,"precisionPorcentaje":2},"firmantes":[],"capitulos":[],"apus":[]}}}
            """.formatted(FINGERPRINT);

    @InjectMock
    S3PrivateObjectStore storage;

    @Test
    void uploadsRenderedPdfAndReturnsOnlyConfirmedMetadata() {
        var confirmed = new S3PrivateObjectStore.StoredObject(KEY, 123, "checksum", "application/pdf", "budget.pdf");
        when(storage.store(eq(KEY), any(byte[].class), eq("application/pdf"), eq("budget.pdf"), eq(FINGERPRINT)))
                .thenReturn(confirmed);
        given().auth()
                .oauth2(ServiceJwtTestProfile.exportToken(OPERATION_ID))
                .contentType("application/json")
                .body(PAYLOAD)
                .when()
                .post(PATH)
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("key", equalTo(KEY))
                .body("checksumSha256", equalTo("checksum"))
                .body("filename", equalTo("budget.pdf"));
        var bytes = ArgumentCaptor.forClass(byte[].class);
        verify(storage).store(eq(KEY), bytes.capture(), eq("application/pdf"), eq("budget.pdf"), eq(FINGERPRINT));
        assertTrue(bytes.getValue().length > 100);
        assertEquals("%PDF", new String(bytes.getValue(), 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void reconcilesByCheckingBoundS3MetadataWithoutDependingOnRerenderedBytes() {
        var confirmed = new S3PrivateObjectStore.StoredObject(KEY, 123, "checksum", "application/pdf", "budget.pdf");
        when(storage.confirmExisting(eq(KEY), eq("application/pdf"), eq("budget.pdf"), eq(FINGERPRINT)))
                .thenReturn(confirmed);
        given().auth()
                .oauth2(ServiceJwtTestProfile.exportToken(OPERATION_ID))
                .contentType("application/json")
                .body(PAYLOAD)
                .when()
                .post(PATH + "/reconcile")
                .then()
                .statusCode(200)
                .body("key", equalTo(KEY));
        verify(storage).confirmExisting(eq(KEY), eq("application/pdf"), eq("budget.pdf"), eq(FINGERPRINT));
    }

    @Test
    void rejectsAnonymousUpload() {
        given().contentType("application/json")
                .body(PAYLOAD)
                .when()
                .post(PATH)
                .then()
                .statusCode(401);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsUserRoleBeforeRenderingOrUploading() {
        String token = ServiceJwtTestProfile.token(
                "core",
                "documentos",
                Set.of("USUARIO"),
                Instant.now().plusSeconds(30).getEpochSecond(),
                false);
        given().auth()
                .oauth2(token)
                .contentType("application/json")
                .body(PAYLOAD)
                .when()
                .post(PATH)
                .then()
                .statusCode(403);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsMalformedUploadBeforeRenderingOrUploading() {
        given().auth()
                .oauth2(ServiceJwtTestProfile.exportToken(OPERATION_ID))
                .contentType("application/json")
                .body(PAYLOAD.replace("\"schemaVersion\":1,\"key\"", "\"schemaVersion\":2,\"key\""))
                .when()
                .post(PATH)
                .then()
                .statusCode(400);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsUnboundOrMismatchedServiceToken() {
        given().auth()
                .oauth2(ServiceJwtTestProfile.serviceToken())
                .contentType("application/json")
                .body(PAYLOAD)
                .when()
                .post(PATH)
                .then()
                .statusCode(401);
        given().auth()
                .oauth2(ServiceJwtTestProfile.exportToken("01900000-0000-7000-8000-000000000009"))
                .contentType("application/json")
                .body(PAYLOAD)
                .when()
                .post(PATH)
                .then()
                .statusCode(401);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsInvalidKeyAndFingerprintWithoutUploading() {
        String token = ServiceJwtTestProfile.exportToken(OPERATION_ID);
        given().auth()
                .oauth2(token)
                .contentType("application/json")
                .body(PAYLOAD.replace(KEY, "exports/------------------------------------"))
                .when()
                .post(PATH)
                .then()
                .statusCode(400);
        given().auth()
                .oauth2(token)
                .contentType("application/json")
                .body(PAYLOAD.replace(FINGERPRINT, "x"))
                .when()
                .post(PATH)
                .then()
                .statusCode(400);
        verifyNoInteractions(storage);
    }
}
